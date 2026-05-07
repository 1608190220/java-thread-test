package com.example.multithread.lock;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 知识点：Condition（条件队列）。
 *
 * 示例目标：
 * 1. 使用一个锁配合多个 Condition，分别管理“队列不满”和“队列非空”。
 * 2. 演示 await/signal 的典型模式。
 * 3. 对比 Object.wait/notify，Condition 语义更清晰。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.lock.ConditionBoundedQueueDemo
 */
public class ConditionBoundedQueueDemo {

    private static final String POISON_PILL = "__STOP__";

    public static void main(String[] args) throws InterruptedException {
        BoundedQueue<String> queue = new BoundedQueue<>(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                for (int i = 1; i <= 6; i++) {
                    String item = "JOB-" + i;
                    queue.put(item);
                    log("生产: " + item);
                    Thread.sleep(120);
                }
                queue.put(POISON_PILL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("生产者中断");
            }
        });

        executor.submit(() -> {
            try {
                while (true) {
                    String item = queue.take();
                    if (POISON_PILL.equals(item)) {
                        log("收到停止信号，消费者退出");
                        break;
                    }
                    Thread.sleep(260);
                    log("消费: " + item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("消费者中断");
            }
        });

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        log("示例结束");
    }

    /**
     * 由 ReentrantLock + Condition 实现的有界队列。
     */
    private static class BoundedQueue<T> {
        private final Deque<T> deque = new ArrayDeque<>();
        private final int capacity;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        BoundedQueue(int capacity) {
            this.capacity = capacity;
        }

        void put(T item) throws InterruptedException {
            lock.lock();
            try {
                // 队列满则等待“非满”条件。
                while (deque.size() >= capacity) {
                    log("队列已满，生产者等待");
                    notFull.await();
                }
                deque.addLast(item);
                // 入队后通知消费者：队列已非空。
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        T take() throws InterruptedException {
            lock.lock();
            try {
                // 队列空则等待“非空”条件。
                while (deque.isEmpty()) {
                    log("队列为空，消费者等待");
                    notEmpty.await();
                }
                T item = deque.removeFirst();
                // 出队后通知生产者：队列已非满。
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}

