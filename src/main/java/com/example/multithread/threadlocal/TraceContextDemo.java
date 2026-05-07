package com.example.multithread.threadlocal;

import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * ThreadLocal / InheritableThreadLocal 示例。
 *
 * 目标：
 * 1. 理解 ThreadLocal 的“线程隔离”语义。
 * 2. 理解 InheritableThreadLocal 只在“创建子线程时复制”。
 * 3. 看清在线程池场景下的上下文传递与污染风险。
 *
 * 运行后建议重点观察：
 * - 哪些任务能看到主线程设置的值；
 * - 哪些任务看不到；
 * - 线程池复用时为什么可能读到“过期上下文”。
 */
public class TraceContextDemo {

    /**
     * 每个线程独享一份副本，不会自动传到别的线程。
     */
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    /**
     * 子线程创建时，会复制父线程当前值。
     * 注意：复制发生在“线程创建时”，不是任务执行时。
     */
    private static final InheritableThreadLocal<String> INHERITABLE_TRACE_ID = new InheritableThreadLocal<>();

    public static void main(String[] args) throws Exception {
        System.out.println("\n===== 1) ThreadLocal：不做传递 =====");
        demoThreadLocalWithoutPropagation();

        System.out.println("\n===== 2) ThreadLocal：通过包装器手动传递 =====");
        demoThreadLocalWithPropagationWrapper();

        System.out.println("\n===== 3) InheritableThreadLocal：新建子线程可继承 =====");
        demoInheritableThreadLocalWithNewThread();

        System.out.println("\n===== 4) InheritableThreadLocal：在线程池中的陷阱 =====");
        demoInheritableThreadLocalInThreadPool();
    }

    /**
     * 场景：主线程设置了 ThreadLocal，但任务提交到线程池后，工作线程默认拿不到值。
     */
    private static void demoThreadLocalWithoutPropagation() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            TRACE_ID.set("TL-" + UUID.randomUUID());
            log("主线程：已设置 ThreadLocal");

            Future<?> future = pool.submit(() -> log("线程池任务：未做传递，读取 ThreadLocal"));
            future.get();
        } finally {
            TRACE_ID.remove();
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * 场景：通过任务包装器，把调用线程上下文注入到执行线程。
     * 关键点：
     * 1. 提交任务时先捕获调用线程的值；
     * 2. 执行任务前注入；
     * 3. finally 中恢复旧值，避免线程池复用带来的脏数据污染。
     */
    private static void demoThreadLocalWithPropagationWrapper() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            TRACE_ID.set("TL-" + UUID.randomUUID());
            log("主线程：准备提交任务（使用包装器）");

            Future<?> future = pool.submit(wrapWithTraceContext(() -> log("线程池任务：读取到包装器传递的 ThreadLocal")));
            future.get();
        } finally {
            TRACE_ID.remove();
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * 场景：直接 new Thread 时，InheritableThreadLocal 会从父线程复制当前值到子线程。
     */
    private static void demoInheritableThreadLocalWithNewThread() throws InterruptedException {
        INHERITABLE_TRACE_ID.set("ITL-" + UUID.randomUUID());
        log("主线程：已设置 InheritableThreadLocal");

        Thread child = new Thread(() -> log("子线程：读取继承到的 InheritableThreadLocal"), "child-thread");
        child.start();
        child.join();

        INHERITABLE_TRACE_ID.remove();
    }

    /**
     * 场景：在线程池中使用 InheritableThreadLocal 的常见误区。
     *
     * 这里用单线程池便于观察：
     * - 第一次提交任务时，工作线程首次创建，可能继承到父线程值 A；
     * - 第二次提交任务时，线程已复用，不会重新继承父线程新值 B；
     * - 结果：任务可能仍看到旧值 A，形成“上下文过期”。
     */
    private static void demoInheritableThreadLocalInThreadPool() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            INHERITABLE_TRACE_ID.set("ITL-FIRST-" + UUID.randomUUID());
            log("主线程：设置第一次值，提交任务");
            pool.submit(() -> log("线程池任务-1：读取 InheritableThreadLocal")).get();

            INHERITABLE_TRACE_ID.set("ITL-SECOND-" + UUID.randomUUID());
            log("主线程：更新为第二次值，再提交任务");
            pool.submit(() -> log("线程池任务-2：如果线程复用，通常还是旧值")).get();

            // 清理工作线程中的历史上下文，避免影响后续示例/任务。
            pool.submit(INHERITABLE_TRACE_ID::remove).get();
        } finally {
            INHERITABLE_TRACE_ID.remove();
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    /**
     * 任务装饰器：
     * 在提交时捕获调用线程的 traceId，在执行时注入到工作线程。
     * 这是 ThreadLocal 在线程池中的常见上下文透传方案。
     */
    private static Runnable wrapWithTraceContext(Runnable task) {
        String capturedTraceId = TRACE_ID.get();
        return () -> {
            String old = TRACE_ID.get();
            try {
                TRACE_ID.set(capturedTraceId);
                task.run();
            } finally {
                // 恢复现场，避免线程池复用导致上下文串号。
                if (old == null) {
                    TRACE_ID.remove();
                } else {
                    TRACE_ID.set(old);
                }
            }
        };
    }

    private static void log(String message) {
        System.out.printf(
                "%s [%s] threadLocal=%s inheritableThreadLocal=%s | %s%n",
                LocalTime.now(),
                Thread.currentThread().getName(),
                TRACE_ID.get(),
                INHERITABLE_TRACE_ID.get(),
                message
        );
    }
}
