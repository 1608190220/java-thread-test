package com.example.multithread.lock;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 知识点：Lock（显式锁接口）。
 *
 * 示例目标：
 * 1. 使用 Lock 接口（而不是具体实现类）声明依赖，降低耦合。
 * 2. 演示 lock/unlock 必须配对，并在 finally 中释放锁。
 * 3. 在并发累加场景下保证临界区串行执行。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.lock.LockInterfaceCriticalSectionDemo
 */
public class LockInterfaceCriticalSectionDemo {

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 8;
        int loopPerThread = 15_000;
        int expected = threadCount * loopPerThread;

        Counter counter = new Counter();
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < loopPerThread; j++) {
                        counter.increment();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        System.out.println("========== Lock 接口临界区演示 ==========");
        System.out.println("理论值 expected = " + expected);
        System.out.println("实际值 actual    = " + counter.get());
        log("结论：通过 Lock 显式控制临界区，保证并发更新正确性。");
    }

    /**
     * 计数器：
     * - 用 Lock 接口声明，便于后续替换锁实现。
     * - unlock 放入 finally，确保异常时也能释放锁。
     */
    private static class Counter {
        private final Lock lock = new ReentrantLock();
        private int value = 0;

        void increment() {
            lock.lock();
            try {
                value++;
            } finally {
                lock.unlock();
            }
        }

        int get() {
            lock.lock();
            try {
                return value;
            } finally {
                lock.unlock();
            }
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}

