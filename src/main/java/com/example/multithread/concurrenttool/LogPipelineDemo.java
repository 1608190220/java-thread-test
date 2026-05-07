package com.example.multithread.concurrenttool;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 生产场景示例：异步日志流水线
 *
 * 场景说明：
 * - 业务线程负责生产日志，不直接做慢 IO
 * - 后台消费者线程批量写出（本示例用打印代替落盘）
 *
 * 价值：
 * - 用队列解耦生产速度和消费速度
 * - 可以通过队列容量和批次大小做流量治理
 */
public class LogPipelineDemo {

    private static final String POISON_PILL = "__STOP__";

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(20);
        ExecutorService producers = Executors.newFixedThreadPool(2);
        ExecutorService consumer = Executors.newSingleThreadExecutor(r -> new Thread(r, "log-consumer"));

        // 启动消费者
        consumer.submit(() -> consumeLogs(queue));

        // 启动生产者，模拟两个业务模块并发打日志
        producers.submit(() -> produceLogs(queue, "order-service", 12));
        producers.submit(() -> produceLogs(queue, "payment-service", 12));

        producers.shutdown();
        producers.awaitTermination(3, TimeUnit.SECONDS);

        // 使用毒丸消息通知消费者停止（常见、简单的退出协议）
        queue.put(POISON_PILL);

        consumer.shutdown();
        consumer.awaitTermination(3, TimeUnit.SECONDS);
        System.out.println("\n日志流水线示例结束。");
    }

    private static void produceLogs(BlockingQueue<String> queue, String source, int count) {
        for (int i = 1; i <= count; i++) {
            String message = LocalTime.now() + " [" + source + "] request-" + i + " handled";
            try {
                queue.put(message);
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void consumeLogs(BlockingQueue<String> queue) {
        List<String> batch = new ArrayList<>(5);
        try {
            while (true) {
                String log = queue.poll(300, TimeUnit.MILLISECONDS);
                if (log == null) {
                    flush(batch);
                    continue;
                }
                if (POISON_PILL.equals(log)) {
                    flush(batch);
                    System.out.println("收到停止信号，消费者退出。");
                    break;
                }
                batch.add(log);
                if (batch.size() >= 5) {
                    flush(batch);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void flush(List<String> batch) {
        if (batch.isEmpty()) {
            return;
        }
        System.out.println("批量写出日志，条数 = " + batch.size());
        for (String line : batch) {
            System.out.println("  -> " + line);
        }
        batch.clear();
    }
}
