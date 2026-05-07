package com.example.multithread.threadpool;

import java.time.LocalTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识点：线程池（ThreadPoolExecutor）核心参数。
 *
 * 示例目标：
 * 1. 观察 corePoolSize / maximumPoolSize / queueCapacity 对任务执行的影响。
 * 2. 演示自定义线程工厂，方便排查日志。
 * 3. 演示拒绝策略在高峰流量下如何兜底。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.threadpool.ThreadPoolExecutorParameterDemo
 */
public class ThreadPoolExecutorParameterDemo {

    public static void main(String[] args) throws InterruptedException {
        int taskCount = 10;
        CountDownLatch doneLatch = new CountDownLatch(taskCount);

        // 自定义线程命名，便于日志定位。
        ThreadFactory namedThreadFactory = new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "tp-worker-" + index.getAndIncrement());
            }
        };

        // 自定义拒绝策略：记录告警并让提交方线程执行任务，避免任务悄悄丢失。
        RejectedExecutionHandler rejectHandler = (task, executor) -> {
            System.out.printf("%s [WARN] 线程池繁忙，触发拒绝策略。active=%d queue=%d%n",
                    LocalTime.now(), executor.getActiveCount(), executor.getQueue().size());
            task.run();
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, // 核心线程数：常驻处理基础流量
                4, // 最大线程数：突发流量时临时扩容
                30, TimeUnit.SECONDS, // 非核心线程空闲存活时间
                new ArrayBlockingQueue<>(2), // 有界队列：限制排队长度，防止无限积压
                namedThreadFactory,
                rejectHandler
        );

        for (int i = 1; i <= taskCount; i++) {
            final String taskName = "TASK-" + i;
            executor.execute(() -> {
                try {
                    logPoolMetrics(executor, taskName + " 开始处理");
                    Thread.sleep(500);
                    logPoolMetrics(executor, taskName + " 处理完成");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.printf("%s [%s] %s 被中断%n",
                            LocalTime.now(), Thread.currentThread().getName(), taskName);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await();
        shutdownGracefully(executor);
        System.out.println("示例结束：请结合日志观察线程扩容、排队与拒绝策略触发时机。");
    }

    private static void logPoolMetrics(ThreadPoolExecutor executor, String message) {
        System.out.printf("%s [%s] %s | active=%d poolSize=%d queue=%d completed=%d%n",
                LocalTime.now(),
                Thread.currentThread().getName(),
                message,
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount());
    }

    private static void shutdownGracefully(ThreadPoolExecutor executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
            int pending = executor.shutdownNow().size();
            System.out.println("[WARN] 线程池未在超时时间内退出，强制关闭。剩余任务数=" + pending);
        }
    }
}
