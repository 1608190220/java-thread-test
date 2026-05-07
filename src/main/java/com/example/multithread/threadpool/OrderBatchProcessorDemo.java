package com.example.multithread.threadpool;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生产场景示例：订单批量处理线程池
 *
 * 示例目标：
 * 1. 演示线程池核心参数如何影响任务执行
 * 2. 演示自定义线程名，便于日志排查
 * 3. 演示拒绝策略，避免任务无提示丢失
 */
public class OrderBatchProcessorDemo {

    public static void main(String[] args) throws InterruptedException {
        List<String> orderIds = buildOrders(20);
        CountDownLatch latch = new CountDownLatch(orderIds.size());

        // 自定义线程工厂：统一线程命名，生产排障时更直观
        ThreadFactory namedFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "order-worker-" + counter.getAndIncrement());
            }
        };

        // 自定义拒绝策略：队列满时回退到调用方线程执行，并记录告警
        RejectedExecutionHandler rejectedHandler = (r, executor) -> {
            System.out.println(LocalTime.now() + " [WARN] 线程池繁忙，任务回退到调用方线程执行");
            r.run();
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, // corePoolSize: 常驻线程
                4, // maximumPoolSize: 峰值线程
                30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(5), // 有界队列，避免无限积压导致 OOM
                namedFactory,
                rejectedHandler
        );

        for (String orderId : orderIds) {
            executor.submit(() -> {
                try {
                    processOrder(orderId);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待全部订单处理完成
        latch.await();
        shutdownGracefully(executor);
        System.out.println("\n全部订单处理结束。");
    }

    private static List<String> buildOrders(int size) {
        List<String> orders = new ArrayList<>(size);
        for (int i = 1; i <= size; i++) {
            orders.add("ORDER-" + i);
        }
        return orders;
    }

    private static void processOrder(String orderId) {
        try {
            // 模拟调用库存、价格、风控等下游服务
            Thread.sleep(150);
            String threadName = Thread.currentThread().getName();
            System.out.printf("%s [%s] 已处理订单 %s%n", LocalTime.now(), threadName, orderId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.printf("[ERROR] 订单 %s 处理被中断%n", orderId);
        }
    }

    private static void shutdownGracefully(ThreadPoolExecutor executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
            List<Runnable> droppedTasks = executor.shutdownNow();
            System.out.println("[WARN] 超时未结束，强制关闭，未执行任务数：" + droppedTasks.size());
        }
    }
}

