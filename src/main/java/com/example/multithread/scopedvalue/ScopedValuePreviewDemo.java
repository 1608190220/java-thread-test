package com.example.multithread.scopedvalue;

import java.lang.ScopedValue;
import java.time.LocalTime;
import java.util.concurrent.StructuredTaskScope;

/**
 * 知识点：ScopedValue（JDK 21 Preview，位于 java.lang，不属于 JUC）。
 *
 * ScopedValue 解决的问题：
 * - 有些上下文是“请求级、只读、只在一段代码作用域内有效”的，例如 traceId、tenantId、登录用户。
 * - ThreadLocal 可以表达这类上下文，但它是可变的、需要手动 remove，在线程池中容易污染。
 * - ScopedValue 把上下文绑定到一个词法作用域：进入作用域时绑定，离开作用域后自动失效。
 *
 * 与 ThreadLocal 的关键区别：
 * 1. ThreadLocal 是线程维度的可变槽位；ScopedValue 是作用域维度的只读绑定。
 * 2. ThreadLocal 通常需要 set/remove；ScopedValue 通过 where(...).run(...) 建立临时绑定。
 * 3. ScopedValue 与结构化并发配合时，子任务可以读取父作用域绑定的值。
 *
 * 注意：
 * - ScopedValue 在 JDK 21 中是 Preview API。
 * - 编译和运行都需要 --enable-preview。
 */
public class ScopedValuePreviewDemo {

    private static final ScopedValue<RequestContext> REQUEST_CONTEXT = ScopedValue.newInstance();

    public static void main(String[] args) throws Exception {
        log("===== ScopedValue Preview 示例 =====");

        RequestContext context = new RequestContext("TRACE-20260507-001", "tenant-a", "U1001");

        log("进入 ScopedValue 作用域前，是否已绑定：" + REQUEST_CONTEXT.isBound());

        ScopedValue.where(REQUEST_CONTEXT, context).run(() -> {
            log("进入作用域后，是否已绑定：" + REQUEST_CONTEXT.isBound());
            handleRequest();

            try {
                handleRequestWithStructuredConcurrency();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        log("离开 ScopedValue 作用域后，是否已绑定：" + REQUEST_CONTEXT.isBound());
    }

    /**
     * 普通调用链读取 ScopedValue。
     *
     * 这里没有显式参数，是为了展示 ScopedValue 的上下文读取能力。
     * 但它仍然比 ThreadLocal 更受约束：值只在 where(...).run(...) 的动态作用域内可见。
     */
    private static void handleRequest() {
        RequestContext context = REQUEST_CONTEXT.get();
        log("普通调用链读取上下文：traceId=" + context.traceId
                + ", tenantId=" + context.tenantId
                + ", userId=" + context.userId);
    }

    /**
     * ScopedValue 和 StructuredTaskScope 的组合。
     *
     * 结构化并发创建的子任务仍然处在父任务的结构化作用域内，
     * 因此可以读取父作用域中绑定的 ScopedValue。
     */
    private static void handleRequestWithStructuredConcurrency() throws Exception {
        try (StructuredTaskScope.ShutdownOnFailure scope = new StructuredTaskScope.ShutdownOnFailure()) {
            StructuredTaskScope.Subtask<String> profileTask = scope.fork(ScopedValuePreviewDemo::queryUserProfile);
            StructuredTaskScope.Subtask<String> orderTask = scope.fork(ScopedValuePreviewDemo::queryRecentOrder);

            scope.join();
            scope.throwIfFailed();

            log("聚合结果：" + profileTask.get() + "，" + orderTask.get());
        }
    }

    private static String queryUserProfile() throws InterruptedException {
        Thread.sleep(150);
        RequestContext context = REQUEST_CONTEXT.get();
        return "用户画像(userId=" + context.userId + ", traceId=" + context.traceId + ")";
    }

    private static String queryRecentOrder() throws InterruptedException {
        Thread.sleep(120);
        RequestContext context = REQUEST_CONTEXT.get();
        return "最近订单(tenantId=" + context.tenantId + ", traceId=" + context.traceId + ")";
    }

    private record RequestContext(String traceId, String tenantId, String userId) {
    }

    private static void log(String message) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), message);
    }
}
