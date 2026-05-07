package com.example.multithread.queue.transferqueue;

import java.time.LocalTime;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;

/**
 * 知识点：LinkedTransferQueue / TransferQueue。
 *
 * TransferQueue 是 BlockingQueue 的增强接口，核心能力是 transfer：
 * 1. put/offer 只负责把元素放入队列，消费者以后再取也可以。
 * 2. transfer 会一直等待，直到某个消费者真正接收到这个元素。
 * 3. tryTransfer 可以尝试立即交付；如果没有消费者等待，可以返回 false 或等待指定时间。
 *
 * LinkedTransferQueue 是 TransferQueue 的常用实现，适合生产者希望知道“任务是否已经被消费者接手”的场景。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.queue.transferqueue.LinkedTransferQueueDemo
 */
public class LinkedTransferQueueDemo {

    public static void main(String[] args) throws InterruptedException {
        TransferQueue<String> queue = new LinkedTransferQueue<>();

        Thread consumer = new Thread(() -> consume(queue), "transfer-consumer");
        consumer.start();

        Thread.sleep(200);

        log("tryTransfer 立即交付：如果此刻消费者正在等待，就直接交给消费者");
        boolean transferredImmediately = queue.tryTransfer("实时消息-1");
        log("实时消息-1 是否立即交付成功 = " + transferredImmediately);

        log("transfer 交付：生产者会等待，直到消费者真正接收到消息");
        queue.transfer("关键消息-2");
        log("关键消息-2 已被消费者接收，生产者继续执行");

        log("tryTransfer(timeout) 交付：最多等待 1 秒");
        boolean transferredWithinTimeout = queue.tryTransfer("限时消息-3", 1, TimeUnit.SECONDS);
        log("限时消息-3 是否在超时时间内交付成功 = " + transferredWithinTimeout);

        consumer.join();
    }

    private static void consume(TransferQueue<String> queue) {
        for (int i = 1; i <= 3; i++) {
            try {
                log("消费者准备 take，第 " + i + " 次");
                String message = queue.take();
                log("消费者收到：" + message);
                Thread.sleep(350);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("消费者被中断");
                return;
            }
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
