package com.example.multithread.atomic;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 知识点：LongAdder（高并发下更友好的计数器）。
 *
 * 示例目标：
 * 1. 在高并发累加场景对比 AtomicLong 与 LongAdder。
 * 2. 理解 LongAdder 通过“分段累加、最终汇总”减少热点竞争。
 * 3. 强调 LongAdder 读取用 sum()，不适合严格实时一致读场景。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.atomic.LongAdderThroughputDemo
 */
public class LongAdderThroughputDemo {

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 12;
        int incrementsPerThread = 200_000;

        long atomicCost = runAtomicLongCase(threadCount, incrementsPerThread);
        long adderCost = runLongAdderCase(threadCount, incrementsPerThread);
        int expected = threadCount * incrementsPerThread;

        System.out.println("========== LongAdder 与 AtomicLong 对比 ==========");
        System.out.println("理论总次数 expected = " + expected);
        System.out.println("AtomicLong 耗时(ms)  = " + atomicCost);
        System.out.println("LongAdder 耗时(ms)   = " + adderCost);
        log("结论：在高并发纯计数写场景，LongAdder 往往具有更好吞吐。");
    }

    private static long runAtomicLongCase(int threadCount, int incrementsPerThread) throws InterruptedException {
        AtomicLong counter = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long start = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        counter.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long costMs = (System.nanoTime() - start) / 1_000_000;
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        log("AtomicLong 计数结果 = " + counter.get());
        return costMs;
    }

    private static long runLongAdderCase(int threadCount, int incrementsPerThread) throws InterruptedException {
        LongAdder adder = new LongAdder();
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long start = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        adder.increment();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long costMs = (System.nanoTime() - start) / 1_000_000;
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        log("LongAdder 计数结果 = " + adder.sum());
        return costMs;
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}

