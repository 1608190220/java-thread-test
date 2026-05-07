package com.example.multithread.concurrenttool;

import java.time.LocalTime;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识点：CyclicBarrier（可循环使用的栅栏）。
 *
 * 示例目标：
 * 1. 演示多线程"分批处理 + 批次对齐"。
 * 2. 演示 barrierAction 在所有参与者到齐后触发。
 * 3. 理解 CyclicBarrier 可以跨多个批次重复使用。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.concurrenttool.CyclicBarrierBatchSyncDemo
 */
public class CyclicBarrierBatchSyncDemo {

    public static void main(String[] args) throws InterruptedException {
        int workers = 3;
        int rounds = 2;
        CountDownLatch doneLatch = new CountDownLatch(workers);
        AtomicInteger roundCounter = new AtomicInteger(1);

        CyclicBarrier barrier = new CyclicBarrier(workers, () -> {
            int currentRound = roundCounter.getAndIncrement();
            log("所有线程到齐，触发批次合并动作。当前批次=" + currentRound);
        });

        ExecutorService executor = Executors.newFixedThreadPool(workers);
        for (int i = 1; i <= workers; i++) {
            int workerId = i;
            executor.submit(() -> runWorker(workerId, rounds, barrier, doneLatch));
        }

        doneLatch.await();
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
        log("示例结束：全部工作线程已完成全部批次");
    }

    private static void runWorker(
            int workerId,
            int rounds,
            CyclicBarrier barrier,
            CountDownLatch doneLatch
    ) {
        try {
            for (int round = 1; round <= rounds; round++) {
                int cost = ThreadLocalRandom.current().nextInt(200, 700);
                Thread.sleep(cost);
                log("worker-" + workerId + " 完成批次 " + round + " 的本地处理，耗时=" + cost + "ms");

                // 等待同批次的其他线程完成，确保批次对齐后再进入下一轮。
                barrier.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("worker-" + workerId + " 被中断");
        } catch (BrokenBarrierException e) {
            log("worker-" + workerId + " 检测到栅栏破损：" + e.getMessage());
        } finally {
            doneLatch.countDown();
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
