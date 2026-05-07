package com.example.multithread.atomic;

import java.time.LocalTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 知识点：AtomicLong（无锁原子长整型）。
 *
 * 示例场景：并发生成本地唯一流水号。
 *
 * 示例目标：
 * 1. 使用 AtomicLong.getAndIncrement 生成单调递增序列。
 * 2. 在多线程下校验“无重复”与“总量正确”。
 * 3. 展示 AtomicLong 在轻量级本地 ID 场景中的用法。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.atomic.AtomicLongSequenceDemo
 */
public class AtomicLongSequenceDemo {

    public static void main(String[] args) throws InterruptedException {
        int threads = 6;
        int idsPerThread = 5_000;
        int expected = threads * idsPerThread;

        AtomicLong sequence = new AtomicLong(1_000_000L);
        Set<Long> idSet = ConcurrentHashMap.newKeySet(expected);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = sequence.getAndIncrement();
                        idSet.add(id);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        long first = 1_000_000L;
        long last = sequence.get() - 1;

        System.out.println("========== AtomicLong 流水号演示 ==========");
        System.out.println("理论生成总数 expected = " + expected);
        System.out.println("实际唯一 ID 总数      = " + idSet.size());
        System.out.println("序列区间              = [" + first + ", " + last + "]");
        log("结论：AtomicLong 可在并发下稳定生成不重复、单调递增的本地序列。");
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}

