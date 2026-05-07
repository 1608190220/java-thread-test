package com.example.multithread.virtualthread;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * ============================================================
 * 知识点：虚拟线程（Virtual Thread）— 三种创建方式完整对比
 * ============================================================
 *
 * 【什么是虚拟线程？】
 * ┌─────────────────────────────────────────────────────────────┐
 * │  虚拟线程（Virtual Thread）是 JDK 21 引入的轻量级线程模型。   │
 * │                                                             │
 * │  传统平台线程（Platform Thread）：                            │
 * │    - 由操作系统内核调度，创建/切换成本高                    │
 * │    - 一个线程约占用 1MB 栈内存                               │
 * │    - 数量受限（通常几千个就是上限）                          │
 * │                                                             │
 * │  虚拟线程（Virtual Thread）：                                │
 * │    - 由 JDK 的 ForkJoinPool 调度器在用户态调度               │
 * │    - 创建成本极低，可以轻松创建百万个                         │
 * │    - 栈内存按需分配，初始仅几KB                              │
 * │    - 阻塞时自动卸载（unmount），不占用载体线程               │
 * │                                                             │
 * │  类比：平台线程 ≈ 重量级卡车（载货多但数量少）              │
 * │       虚拟线程   ≈ 自行车（轻便灵活，数量可极多）            │
 * └─────────────────────────────────────────────────────────────┘
 *
 * 【本 Demo 演示的三种创建虚拟线程的方式】
 *
 * ┌────────────────┬──────────────────────────┬─────────────────────┐
 * │  方式           │  API                      │  适用场景            │
 * ├────────────────┼──────────────────────────┼─────────────────────┤
 * │  ① 工厂方法     │  Executors.newVirtual     │  最常用、最简单      │
 * │  （快速入门）   │  ThreadPerTaskExecutor()  │  每任务一VT          │
 * ├────────────────┼──────────────────────────┼─────────────────────┤
 * │  ② Builder模式 │  Thread.ofVirtual()       │  需要精细控制单个VT  │
 * │  (手动创建)    │  .name()/.start()         │  命名、延迟启动等    │
 * ├────────────────┼──────────────────────────┼─────────────────────┤
 * │  ③ 工厂+池化   │  Thread.ofVirtual()       │  需要自定义执行器    │
 * │  (高级定制)    │  .factory() + ExecutorSvc │  统一命名/守护等     │
 * └────────────────┴──────────────────────────┴─────────────────────┘
 *
 */
public class VirtualThreadApiAggregationDemo {

    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws Exception {
        log("============================================================");
        log("  虚拟线程（Virtual Thread）— 三种创建方式完整演示");
        log("============================================================");

        // ================================================================
        // 演示①：Executors.newVirtualThreadPerTaskExecutor() — 最常用方式
        // ================================================================
        System.out.println();
        demonstrateFactoryMethod();

        // ================================================================
        // 演示②：Thread.ofVirtual() Builder — 手动创建单个虚拟线程
        // ================================================================
        System.out.println();
        demonstrateBuilderPattern();

        // ================================================================
        // 演示③：Thread.ofVirtual().factory() — 自定义工厂 + 线程池
        // ================================================================
        System.out.println();
        demonstrateCustomFactoryWithExecutor();

        log("");
        log("============================================================");
        log("  全部演示结束！");
        log("============================================================");
    }

    /**
     * ================================================================
     * 演示①：Executors.newVirtualThreadPerTaskExecutor()
     * ================================================================
     *
     * 【这是什么？】
     * JDK 21 提供的"开箱即用"工厂方法，返回一个特殊的 ExecutorService。
     * 每次 submit/execute 都会创建一个新的虚拟线程来执行任务。
     *
     * 【特点】
     * - 无参调用，零配置即可使用
     * - 实现 AutoCloseable，支持 try-with-resources 自动关闭
     * - close() 会等待所有已提交的任务完成后才退出
     * - 内部使用默认的 ForkJoinPool 作为载体线程调度器
     *
     * 【适用场景】
     * "每个请求/任务对应一个独立线程"的模式——这正是绝大多数业务场景。
     * 如：HTTP请求处理、API聚合调用、批处理任务等。
     */
    private static void demonstrateFactoryMethod() throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  演示①：newVirtualThreadPerTaskExecutor（工厂方法）        ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        List<String> apis = List.of("user-service", "order-service", "inventory-service", "coupon-service");

        log("待调用的下游服务：" + apis);
        log("");

        // 创建虚拟线程执行器（无参，最简单的用法）
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ApiResult>> futures = new ArrayList<>();

            for (String api : apis) {
                Callable<ApiResult> task = () -> callDownstreamApi(api, 1000, 3000);
                futures.add(executor.submit(task));
            }

            List<ApiResult> results = new ArrayList<>();
            for (Future<ApiResult> future : futures) {
                results.add(future.get());
            }

            printResults("工厂方法结果", results);
        }
    }

    /**
     * ================================================================
     * 演示②：Thread.ofVirtual() Builder 模式 — 手动创建单个虚拟线程
     * ================================================================
     *
     * 【这是什么？】
     * Thread.ofVirtual() 返回一个 Builder 对象，可以链式配置虚拟线程的属性，
     * 然后通过 .start() 或 .unstarted() 来创建并（可选地）启动线程。
     *
     * 【Builder 可配置的属性】
     * ┌─────────────┬────────────────────────────────────────────────┐
     * │  方法        │  说明                                          │
     * ├─────────────┼────────────────────────────────────────────────┤
     * │ .name(prefix)│ 设置线程名前缀                                 │
     * │ .name(p, n) │ 设置前缀 + 起始计数器值（如 "worker-", 1 → worker-1）│
     * │ .priority(n)│ 设置线程优先级（1-10，默认5，对虚拟线程意义不大）  │
     * │ .unstarted()│ 创建但不立即启动，返回未启动的 Thread 对象       │
     * │ .start(r)   │ 创建并立即启动，传入 Runnable                    │
     * │ .factory()  │ 不创建线程，而是返回一个 ThreadFactory 工厂      │
     * └─────────────┴────────────────────────────────────────────────┘
     *
     * 【注意】daemon() 方法仅适用于 Thread.ofPlatform()，不适用于 ofVirtual()！
     * 虚拟线程的 isDaemon() 始终为 true，但 JVM 退出行为由引用和等待关系决定。
     *
     * 【与 new Thread() 的区别】
     * Thread.ofVirtual()  → 创建的是**虚拟线程**
     * Thread.ofPlatform() → 创建的是**传统平台线程**
     * new Thread()        → 默认也是平台线程
     *
     * 【适用场景】
     * - 需要给虚拟线程起有意义的名字（方便日志排查）
     * - 需要控制线程是否为守护线程
     * - 需要先创建线程但稍后再启动（unstarted）
     * - 只需要少量几个虚拟线程，不想用完整的 ExecutorService
     */
    private static void demonstrateBuilderPattern() throws InterruptedException {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  演示②：Thread.ofVirtual() Builder（手动创建单个VT）     ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        log("--- 2a) 使用 .name() + .start() 创建并立即启动 ---");
        log("");

        // 用 Builder 创建一个带自定义名称的虚拟线程
        // name("api-call-", 1) 表示命名前缀为 "api-call-"，起始编号从1开始
        // 所以第一个线程名叫 "api-call-1"，第二个叫 "api-call-2"，以此类推
        Thread vt1 = Thread.ofVirtual()
                .name("api-call-", 1)
                .start(() -> {
                    try {
                        ApiResult result = callDownstreamApi("product-service", 1000, 3000);
                        log("[手动VT] " + result.toString());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

        // 等待该虚拟线程执行完成
        // join() 是阻塞调用——当前(main)线程会等待 vt1 执行完毕后才继续
        vt1.join();

        log("");
        log("--- 2b) 使用 .unstarted() 创建但不立即启动 ---");
        log("");

        // unstarted() 创建了一个尚未启动的虚拟线程
        // 你可以在合适的时机再调用 .start() 来启动它
        // 这在某些需要"准备阶段"和"执行阶段"分离的场景中很有用
        Thread unstartedVt = Thread.ofVirtual()
                .name("delayed-task-", 1)
                .unstarted(() -> {
                    try {
                        ApiResult result = callDownstreamApi("report-service", 1500, 2500);
                        log("[延迟启动VT] " + result.toString());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

        log("[主线程] 延迟任务已创建但尚未启动...模拟一些准备工作...");
        Thread.sleep(500); // 模拟准备工作耗时

        log("[主线程] 准备工作完成，现在正式启动延迟任务！");
        unstartedVt.start(); // 在这里才真正启动
        unstartedVt.join();  // 等待其完成

        log("");
        log("--- 2c) 使用 .daemon(true) 创建守护虚拟线程 ---");
        log("");

        // 守护线程（Daemon Thread）的特点：
        // 当所有非守护线程（用户线程）结束时，JVM 会直接退出，
        // 不管守护线程是否还在运行。守护线程会被强制终止。
        //
        // 【关于虚拟线程与守护属性】
        // 虚拟线程（Virtual Thread）的 daemon 属性比较特殊：
        // - Thread.ofVirtual() 创建的虚拟线程，其 isDaemon() 始终返回 true
        // - 但这并不影响行为：只要还有任何"存活的"虚拟线程在执行，
        //   JVM 不会退出（因为它们被 ExecutorService 或 join() 管理着）
        // - 只有当没有任何引用指向这些 VT 且没有人在等待时，JVM 才会退出
        // - 所以对于虚拟线程来说，daemon 属性更多是一个标识而非控制机制
        CountDownLatch latch = new CountDownLatch(1);

        Thread daemonVt = Thread.ofVirtual()
                .name("daemon-monitor-", 1)
                .start(() -> {
                    try {
                        for (int i = 0; i < 5; i++) {
                            log("[守护VT] 心跳检测第 " + (i + 1) + " 次...");
                            Thread.sleep(500);
                        }
                        log("[守护VT] 心跳检测全部完成！");
                        latch.countDown(); // 通知主线程
                    } catch (InterruptedException e) {
                        log("[守护VT] 被中断退出");
                    }
                });

        // 主线程等待守护线程完成（仅用于演示）
        // 实际生产中如果 main 直接结束，daemonVt 会被 JVM 强制终止
        latch.await();
        log("[主线程] 收到守护线程完成信号");
    }

    /**
     * ================================================================
     * 演示③：Thread.ofVirtual().factory() + 自定义 ExecutorService
     * ================================================================
     *
     * 【这是什么？】
     * Thread.ofVirtual().factory() 返回一个 ThreadFactory 对象。
     * 这个工厂每次调用 newThread(Runnable) 时都会创建一个新的虚拟线程，
     * 并且所有通过该工厂创建的虚拟线程都具有相同的配置属性（名称前缀、守护状态等）。
     *
     * 将这个 factory 传给 Executors.newThreadPerTaskExecutor(factory)，
     * 就能得到一个"每任务一个虚拟线程"的自定义执行器。
     *
     * 【与 newVirtualThreadPerTaskExecutor() 的区别】
     * ┌──────────────────────────┬──────────────────────────────────┐
     * │  newVirtualThreadPerTask │  ofVirtual().factory() +          │
     * │  Executor()              │  newThreadPerTaskExecutor(factory)│
     * ├──────────────────────────┼──────────────────────────────────┤
     * │  无参数可配              │  可自定义名称前缀                  │
     * │  线程名由JVM自动生成     │  可设置守护线程属性                │
     * │  最简单快捷              │  更灵活可控                       │
     * │  适合90%的场景            │  需要统一管理线程属性时使用        │
     * └──────────────────────────┴──────────────────────────────────┘
     *
     * 【注意】
     * 这里用的是 Executors.newThreadPerTaskExecutor(ThreadFactory)（JDK 19 新增）
     * 而不是 newVirtualThreadPerTaskExecutor()！
     * 区别在于：前者接受外部传入的 ThreadFactory，
     * 后者是内部硬编码使用虚拟线程工厂且不接受参数。
     *
     * 【适用场景】
     * - 需要给所有虚拟线程统一添加命名规范（如 "order-pool-1", "order-pool-2"...）
     * - 需要将所有虚拟线程设为守护线程
     * - 需要在 ThreadFactory 中加入额外的逻辑（如 MDC 上下文传递、监控埋点）
     */
    private static void demonstrateCustomFactoryWithExecutor() throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  演示③：Thread.ofVirtual().factory() + 自定义线程池      ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // 第一步：用 Builder 创建一个配置好的虚拟线程工厂
        // 所有通过这个工厂创建的 VT 都会有统一的命名规则
        ThreadFactory virtualFactory = Thread.ofVirtual()
                .name("custom-api-vt-", 1) // 线程名格式: custom-api-vt-1, custom-api-vt-2, ...
                // .daemon(true)             // 如果需要，也可以设为守护线程
                .factory();                 // 返回 ThreadFactory 实例

        log("已创建自定义虚拟线程工厂，命名规则：custom-api-vt-{序号}");
        log("");

        // 第二步：用自定义工厂创建 ExecutorService
        // newThreadPerTaskExecutor 接受任意 ThreadFactory
        // 每次提交任务时，它会调用 factory.newThread(runnable) 来创建新线程
        // 因为我们传入的是虚拟线程工厂，所以创建出的都是虚拟线程
        try (ExecutorService customPool = Executors.newThreadPerTaskExecutor(virtualFactory)) {
            List<String> apis = List.of(
                    "payment-service", "logistics-service",
                    "notification-service", "analytics-service"
            );

            log("使用自定义线程池提交 " + apis.size() + " 个任务...");
            log("");

            List<Future<ApiResult>> futures = new ArrayList<>();
            for (String api : apis) {
                Callable<ApiResult> task = () -> callDownstreamApi(api, 1000, 4000);
                futures.add(customPool.submit(task));
            }

            List<ApiResult> results = new ArrayList<>();
            for (Future<ApiResult> future : futures) {
                results.add(future.get());
            }

            printResults("自定义工厂线程池结果", results);

            // 观察重点：检查输出中的线程名是否都是 "custom-api-vt-x" 格式
            // 这证明了我们的自定义工厂确实生效了
        }
    }

    // ====================================================================
    // 以下为工具方法和内部类
    // ====================================================================

    /**
     * 模拟一次下游 API 远程调用。
     *
     * @param apiName    下游服务名称
     * @param minDelayMs 最小等待时间（毫秒）
     * @param maxDelayMs 最大等待范围（毫秒），实际延迟 = minDelayMs + [0, maxDelayMs)
     * @return 调用结果快照
     * @throws InterruptedException sleep被中断时抛出
     */
    private static ApiResult callDownstreamApi(String apiName, int minDelayMs, int maxDelayMs) throws InterruptedException {
        int delayMs = minDelayMs + RANDOM.nextInt(maxDelayMs);

        long startTime = System.currentTimeMillis();
        log("[" + apiName + "] 开始调用，预计等待约 " + String.format("%.1f", delayMs / 1000.0) + " 秒...");

        // Thread.sleep() 触发虚拟线程的卸载/重载机制：
        // 阻塞时VT从载体线程卸载，唤醒后重新挂载到某个载体线程继续执行
        Thread.sleep(delayMs);

        long actualMs = System.currentTimeMillis() - startTime;

        return new ApiResult(apiName, "OK", (int) actualMs,
                Thread.currentThread().toString(), LocalTime.now().toString());
    }

    /**
     * 以表格形式打印聚合结果。
     *
     * @param title   结果标题
     * @param results 结果列表
     */
    private static void printResults(String title, List<ApiResult> results) {
        System.out.println("");
        System.out.println("┌──────────────────────────────────────────────────────────────────┐");
        System.out.printf("│  %s%56s%n", title, "│");
        System.out.println("├──────────────────────────────────────────────────────────────────┤");

        int totalCost = 0;
        for (int i = 0; i < results.size(); i++) {
            ApiResult r = results.get(i);
            totalCost += r.costMs;
            System.out.printf("│ [%d] %-22s 耗时=%-5dms  %-35s │%n",
                    i + 1, r.apiName, r.costMs, r.worker.substring(0, Math.min(r.worker.length(), 33)));
        }

        System.out.println("├──────────────────────────────────────────────────────────────────┤");
        System.out.printf("│ 共 %d 个任务  总耗时累加=%dms  （并行执行，实际总耗时≈最慢的那个）%15s%n",
                results.size(), totalCost, "│");
        System.out.println("└──────────────────────────────────────────────────────────────────┘");
    }

    /**
     * API 调用结果数据类。
     */
    static class ApiResult {
        final String apiName;
        final String status;
        final int costMs;
        final String worker;
        final String finishedAt;

        ApiResult(String apiName, String status, int costMs, String worker, String finishedAt) {
            this.apiName = apiName;
            this.status = status;
            this.costMs = costMs;
            this.worker = worker;
            this.finishedAt = finishedAt;
        }

        @Override
        public String toString() {
            return String.format("ApiResult{api='%s', status='%s', costMs=%dms, worker='%s', at='%s'}",
                    apiName, status, costMs, worker, finishedAt);
        }
    }

    /**
     * 统一日志输出：[时间] [线程名] 消息
     */
    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
