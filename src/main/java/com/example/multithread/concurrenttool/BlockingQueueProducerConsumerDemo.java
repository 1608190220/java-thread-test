package com.example.multithread.concurrenttool;

import java.time.LocalTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 知识点：阻塞队列（BlockingQueue）生产者-消费者模型。
 *
 * 示例目标：
 * 1. 演示 put/take 的阻塞行为。
 * 2. 演示队列在生产快于消费时提供"背压"能力。
 * 3. 演示如何用"毒丸消息"优雅结束消费者。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.concurrenttool.BlockingQueueProducerConsumerDemo
 */
public class BlockingQueueProducerConsumerDemo {

    private static final String POISON_PILL = "__STOP__";

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);

        Thread producer = new Thread(() -> produceLogs(queue), "producer");
        Thread consumer = new Thread(() -> consumeLogs(queue), "consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();
        log("示例结束：生产与消费已优雅退出");
    }

    private static void produceLogs(BlockingQueue<String> queue) {
        try {
            for (int i = 1; i <= 6; i++) {
                String logLine = "LOG-" + i;
                log("准备入队：" + logLine + "，当前队列长度=" + queue.size());
                queue.put(logLine); // 队列满时阻塞，形成自然限流
                log("入队完成：" + logLine + "，当前队列长度=" + queue.size());
                Thread.sleep(120); // 模拟"生产速度较快"
            }
            queue.put(POISON_PILL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("生产者被中断，提前退出");
        }
    }

    private static void consumeLogs(BlockingQueue<String> queue) {
        try {
            while (true) {
                String logLine = queue.take(); // 队列空时阻塞等待
                if (POISON_PILL.equals(logLine)) {
                    log("收到停止信号，消费者退出");
                    break;
                }

                // 模拟慢消费：可以更明显看到队列积压与生产者阻塞。
                Thread.sleep(300);
                log("消费完成：" + logLine + "，当前队列长度=" + queue.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("消费者被中断，提前退出");
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
