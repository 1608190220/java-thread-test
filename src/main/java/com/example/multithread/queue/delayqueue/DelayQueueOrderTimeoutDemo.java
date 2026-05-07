package com.example.multithread.queue.delayqueue;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * 知识点：DelayQueue。
 *
 * DelayQueue 是一个无界阻塞队列，队列元素必须实现 Delayed：
 * 1. 元素只有到期后才能被 take() 取出。
 * 2. 队列内部按剩余延迟时间排序，到期时间最早的元素排在前面。
 * 3. 常见场景：订单超时关闭、缓存过期、延迟重试、定时提醒。
 *
 * 本示例模拟“订单超过指定时间未支付就自动关闭”。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.queue.delayqueue.DelayQueueOrderTimeoutDemo
 */
public class DelayQueueOrderTimeoutDemo {

    public static void main(String[] args) throws InterruptedException {
        DelayQueue<OrderTimeoutTask> delayQueue = new DelayQueue<>();

        List<OrderTimeoutTask> tasks = List.of(
                new OrderTimeoutTask("ORDER-1001", 900),
                new OrderTimeoutTask("ORDER-1002", 300),
                new OrderTimeoutTask("ORDER-1003", 600)
        );

        tasks.forEach(task -> {
            delayQueue.offer(task);
            log("提交订单超时任务：" + task.orderId + "，延迟=" + task.delayMillis + "ms");
        });

        Thread closer = new Thread(() -> closeExpiredOrders(delayQueue, tasks.size()), "order-timeout-closer");
        closer.start();
        closer.join();
    }

    private static void closeExpiredOrders(DelayQueue<OrderTimeoutTask> delayQueue, int expectedCount) {
        for (int i = 0; i < expectedCount; i++) {
            try {
                // 如果队头元素还没到期，take() 会阻塞。
                // 因此这里不会按 offer 顺序输出，而是按到期时间输出。
                OrderTimeoutTask task = delayQueue.take();
                log("订单超时，自动关闭：" + task.orderId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("关闭订单线程被中断");
                return;
            }
        }
    }

    private static class OrderTimeoutTask implements Delayed {
        private final String orderId;
        private final long delayMillis;
        private final long deadlineNanos;

        private OrderTimeoutTask(String orderId, long delayMillis) {
            this.orderId = orderId;
            this.delayMillis = delayMillis;
            this.deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            return unit.convert(remainingNanos, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            OrderTimeoutTask that = (OrderTimeoutTask) other;
            return Long.compare(this.deadlineNanos, that.deadlineNanos);
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
