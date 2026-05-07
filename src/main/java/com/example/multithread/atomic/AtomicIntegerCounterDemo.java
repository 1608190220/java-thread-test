package com.example.multithread.atomic;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识点：AtomicInteger（无锁原子整型计数）。
 *
 * 示例目标：
 * 1. 对比普通 int 计数与 AtomicInteger 计数在并发场景下的差异。
 * 2. 理解 incrementAndGet / getAndIncrement 的原子语义。
 * 3. 通过可重复运行的代码直观看到“丢失更新”问题。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.atomic.AtomicIntegerCounterDemo
 */
public class AtomicIntegerCounterDemo {

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 8;
        int loopPerThread = 20_000;
        int expected = threadCount * loopPerThread;

        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // 非线程安全计数器：故意不加锁，演示并发下的丢失更新。
        PlainCounter plainCounter = new PlainCounter();
        AtomicInteger atomicCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < loopPerThread; j++) {
                        plainCounter.value++;               // 非原子操作：读-改-写会被并发打断
                        atomicCounter.incrementAndGet();    // 原子递增：CAS + 重试
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        System.out.println("========== AtomicInteger 并发计数演示 ==========");
        System.out.println("理论总次数 expected = " + expected);
        System.out.println("普通 int 结果        = " + plainCounter.value);
        System.out.println("AtomicInteger 结果   = " + atomicCounter.get());
        log("结论：AtomicInteger 在高并发下能保证计数准确，普通 int 可能出现丢失更新。");
    }

    /**
     * 仅用于演示非线程安全计数。
     */
    private static class PlainCounter {
        private int value;
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}

