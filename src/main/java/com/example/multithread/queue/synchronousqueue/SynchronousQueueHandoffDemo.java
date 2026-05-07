package com.example.multithread.queue.synchronousqueue;

import java.time.LocalTime;
import java.util.concurrent.SynchronousQueue;

/**
 * 知识点：SynchronousQueue。
 *
 * SynchronousQueue 是一个“没有容量”的阻塞队列：
 * 1. put() 必须等待某个线程 take()，元素不会真正存储在队列里。
 * 2. take() 必须等待某个线程 put()，否则也会阻塞。
 * 3. 它适合直接交接任务，例如 cached thread pool 内部就使用了 SynchronousQueue。
 *
 * 本示例模拟“前台接待员”和“后台处理员”直接交接工单。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.queue.synchronousqueue.SynchronousQueueHandoffDemo
 */
public class SynchronousQueueHandoffDemo {

    public static void main(String[] args) throws InterruptedException {
        SynchronousQueue<String> queue = new SynchronousQueue<>();

        Thread producer = new Thread(() -> submitTickets(queue), "ticket-producer");
        Thread consumer = new Thread(() -> handleTickets(queue), "ticket-consumer");

        consumer.start();
        producer.start();

        producer.join();
        consumer.join();
    }

    private static void submitTickets(SynchronousQueue<String> queue) {
        for (int i = 1; i <= 3; i++) {
            String ticket = "工单-" + i;
            try {
                log("准备交接 " + ticket + "，如果没有消费者会阻塞");
                queue.put(ticket);
                log(ticket + " 已被消费者接走");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("提交工单时被中断");
                return;
            }
        }
    }

    private static void handleTickets(SynchronousQueue<String> queue) {
        for (int i = 1; i <= 3; i++) {
            try {
                Thread.sleep(300);
                String ticket = queue.take();
                log("收到并处理 " + ticket);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("处理工单时被中断");
                return;
            }
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
