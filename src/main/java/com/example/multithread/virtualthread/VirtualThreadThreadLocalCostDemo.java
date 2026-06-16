package com.example.multithread.virtualthread;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 知识点：虚拟线程下 ThreadLocal 的成本和替代思路。
 *
 * 虚拟线程让“一个请求一个线程”变得很便宜，但这不代表 ThreadLocal 也可以无限制使用。
 *
 * 需要特别注意：
 * 1. 虚拟线程数量可以非常多，如果每个虚拟线程都写入较大的 ThreadLocal 对象，
 *    总内存会随虚拟线程数量线性增长。
 * 2. 虚拟线程通常不复用，所以传统线程池里常见的“忘记 remove 导致下一个任务串号”
 *    风险会降低，但 ThreadLocal 对象仍会至少跟随该虚拟线程存活到任务结束。
 * 3. ThreadLocal 是隐式上下文，调用链越深越难看出数据从哪里来，也越难测试。
 *
 * 替代思路：
 * - 优先显式传参：最清楚，最容易测试。
 * - 对请求级只读上下文，JDK 21 可以关注 ScopedValue（Preview，位于 java.lang）。
 * - 日志 MDC 等已有生态仍可能依赖 ThreadLocal，但要控制写入对象大小和生命周期。
 */
public class VirtualThreadThreadLocalCostDemo {

    private static final int TASK_COUNT = 20_000;
    private static final ThreadLocal<RequestContext> REQUEST_CONTEXT = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        log("===== 虚拟线程下 ThreadLocal 成本观察 =====");
        log("任务数量：" + TASK_COUNT);

        Result threadLocalResult = runWithThreadLocal();
        Result explicitParameterResult = runWithExplicitParameter();

        printResult("ThreadLocal 隐式上下文", threadLocalResult);
        printResult("显式参数传递", explicitParameterResult);

        log("\n观察结论：");
        log("1. 虚拟线程本身很轻量，但 ThreadLocal 中放入的业务对象不会变轻。");
        log("2. 如果每个虚拟线程都创建大对象放进 ThreadLocal，总分配量会非常可观。");
        log("3. 只读请求上下文优先考虑显式传参或 ScopedValue；必须用 ThreadLocal 时，值要小，并在 finally 中 remove。");
    }

    /**
     * 每个虚拟线程都向 ThreadLocal 写入一个 RequestContext。
     *
     * 这个写法在日志 traceId、租户 id、用户 id 等场景里很常见。
     * 示例里仍然在 finally 中 remove，是为了保留正确习惯：
     * - 平台线程池中必须 remove，避免线程复用污染；
     * - 虚拟线程中 remove 不是总能显著改变结果，但能缩短引用存活时间，降低误用风险。
     */
    private static Result runWithThreadLocal() throws Exception {
        long startNanos = System.nanoTime();
        long checksum = 0;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>(TASK_COUNT);

            for (int i = 0; i < TASK_COUNT; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    RequestContext context = RequestContext.create(index);
                    REQUEST_CONTEXT.set(context);
                    try {
                        return handleRequestByThreadLocal();
                    } finally {
                        REQUEST_CONTEXT.remove();
                    }
                }));
            }

            for (Future<Integer> future : futures) {
                checksum += future.get();
            }
        }

        return new Result(System.nanoTime() - startNanos, checksum);
    }

    /**
     * 显式传参版本。
     *
     * 它没有隐藏状态，handleRequestByExplicitParameter 的输入从方法签名就能看出来。
     * 这类写法通常更适合业务代码、单元测试和长期维护。
     */
    private static Result runWithExplicitParameter() throws Exception {
        long startNanos = System.nanoTime();
        long checksum = 0;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>(TASK_COUNT);

            for (int i = 0; i < TASK_COUNT; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    RequestContext context = RequestContext.create(index);
                    return handleRequestByExplicitParameter(context);
                }));
            }

            for (Future<Integer> future : futures) {
                checksum += future.get();
            }
        }

        return new Result(System.nanoTime() - startNanos, checksum);
    }

    private static int handleRequestByThreadLocal() {
        RequestContext context = REQUEST_CONTEXT.get();
        return context.traceId.length() + context.tenantId.length() + context.payload.length;
    }

    private static int handleRequestByExplicitParameter(RequestContext context) {
        return context.traceId.length() + context.tenantId.length() + context.payload.length;
    }

    private static void printResult(String name, Result result) {
        log(String.format("%s：耗时 %.2fms，checksum=%d",
                name, result.elapsedNanos / 1_000_000.0, result.checksum));
    }

    private record Result(long elapsedNanos, long checksum) {
    }

    /**
     * 模拟请求上下文。
     *
     * payload 用来代表一些容易被误放进 ThreadLocal 的对象，例如用户快照、
     * 权限列表、临时缓存等。真实项目里这些对象可能比这里大得多。
     */
    private record RequestContext(String traceId, String tenantId, byte[] payload) {
        private static RequestContext create(int index) {
            return new RequestContext("TRACE-" + index + "-" + UUID.randomUUID(), "tenant-a", new byte[256]);
        }
    }

    private static void log(String message) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), message);
    }
}
