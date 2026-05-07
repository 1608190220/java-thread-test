package com.example.multithread.lock;

import java.time.LocalTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.StampedLock;

/**
 * 知识点：StampedLock（带戳锁，支持乐观读）。
 *
 * 示例目标：
 * 1. 演示 tryOptimisticRead + validate 的乐观读路径。
 * 2. 演示当校验失败时，回退到悲观读锁保证一致性。
 * 3. 适合理解“读多写少”场景的优化思路。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.lock.StampedLockOptimisticReadDemo
 */
public class StampedLockOptimisticReadDemo {

    public static void main(String[] args) throws InterruptedException {
        Point point = new Point();
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // 写线程：持续更新坐标，制造读写竞争。
        executor.submit(() -> {
            for (int i = 0; i < 10; i++) {
                double deltaX = ThreadLocalRandom.current().nextDouble(-2.0, 2.0);
                double deltaY = ThreadLocalRandom.current().nextDouble(-2.0, 2.0);
                point.move(deltaX, deltaY);
                log("写入坐标增量: dx=" + format(deltaX) + ", dy=" + format(deltaY));
                sleep(120);
            }
        });

        // 读线程：尝试乐观读取距离。
        executor.submit(() -> readDistanceLoop(point, "reader-1"));
        executor.submit(() -> readDistanceLoop(point, "reader-2"));

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        log("示例结束");
    }

    private static void readDistanceLoop(Point point, String readerName) {
        for (int i = 0; i < 12; i++) {
            double distance = point.distanceFromOrigin();
            log(readerName + " 读取距离 = " + format(distance));
            sleep(90);
        }
    }

    /**
     * 二维点：
     * - move: 写锁修改坐标；
     * - distanceFromOrigin: 先乐观读，失败后回退读锁。
     */
    private static class Point {
        private double x;
        private double y;
        private final StampedLock lock = new StampedLock();

        void move(double deltaX, double deltaY) {
            long stamp = lock.writeLock();
            try {
                x += deltaX;
                y += deltaY;
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        double distanceFromOrigin() {
            long stamp = lock.tryOptimisticRead();
            double currentX = x;
            double currentY = y;

            // 如果期间发生写入，乐观读失效，回退到悲观读锁重读。
            if (!lock.validate(stamp)) {
                stamp = lock.readLock();
                try {
                    currentX = x;
                    currentY = y;
                } finally {
                    lock.unlockRead(stamp);
                }
            }

            return Math.sqrt(currentX * currentX + currentY * currentY);
        }
    }

    private static String format(double value) {
        return String.format("%.3f", value);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}

