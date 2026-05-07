package com.example.multithread.concurrenttool;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 知识点：Semaphore（并发访问限流）。
 *
 * 示例目标：
 * 1. 用许可证数量控制"同一时刻最多几个任务并发执行"。
 * 2. 理解 acquire/release 必须成对出现，release 要放在 finally。
 * 3. 观察公平信号量（fair=true）下的排队行为。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.concurrenttool.SemaphoreConcurrencyControlDemo
 */
public class SemaphoreConcurrencyControlDemo {

    public static void main(String[] args) throws InterruptedException {
        int totalCars = 8;
        int parkingSpaces = 3;

        // fair=true：先到先得，减少"后来者插队"。
        Semaphore semaphore = new Semaphore(parkingSpaces, true);
        CountDownLatch doneLatch = new CountDownLatch(totalCars);
        ExecutorService executor = Executors.newFixedThreadPool(totalCars);

        for (int i = 1; i <= totalCars; i++) {
            int carId = i;
            executor.submit(() -> {
                boolean acquired = false;
                try {
                    log("车辆-" + carId + " 到达停车场，等待车位");
                    semaphore.acquire();
                    acquired = true;
                    log("车辆-" + carId + " 已进入，剩余车位=" + semaphore.availablePermits());

                    Thread.sleep(ThreadLocalRandom.current().nextInt(300, 800));
                    log("车辆-" + carId + " 准备离场");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log("车辆-" + carId + " 等待/停车被中断");
                } finally {
                    // 只有成功获取过许可证时才释放，避免"过度释放"导致并发控制失真。
                    if (acquired) {
                        semaphore.release();
                    }
                    doneLatch.countDown();
                    log("车辆-" + carId + " 已离场，剩余车位=" + semaphore.availablePermits());
                }
            });
        }

        doneLatch.await();
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
        log("示例结束：所有车辆处理完成");
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
