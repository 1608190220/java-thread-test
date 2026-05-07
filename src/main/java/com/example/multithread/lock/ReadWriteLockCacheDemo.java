package com.example.multithread.lock;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 知识点：ReadWriteLock（读写分离锁）。
 *
 * 示例目标：
 * 1. 读操作走读锁，可并发读取。
 * 2. 写操作走写锁，与读写都互斥，保证数据一致性。
 * 3. 演示缓存读多写少场景下的典型用法。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.lock.ReadWriteLockCacheDemo
 */
public class ReadWriteLockCacheDemo {

    public static void main(String[] args) throws InterruptedException {
        ProductCache cache = new ProductCache();
        cache.put("SKU-1001", 100);
        cache.put("SKU-1002", 80);

        int readerTasks = 8;
        int writerTasks = 2;
        CountDownLatch doneLatch = new CountDownLatch(readerTasks + writerTasks);
        ExecutorService executor = Executors.newFixedThreadPool(6);

        for (int i = 0; i < readerTasks; i++) {
            executor.submit(() -> {
                try {
                    String sku = ThreadLocalRandom.current().nextBoolean() ? "SKU-1001" : "SKU-1002";
                    int stock = cache.get(sku);
                    log("读取库存: " + sku + " -> " + stock);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        for (int i = 0; i < writerTasks; i++) {
            executor.submit(() -> {
                try {
                    String sku = ThreadLocalRandom.current().nextBoolean() ? "SKU-1001" : "SKU-1002";
                    int newValue = ThreadLocalRandom.current().nextInt(50, 120);
                    cache.put(sku, newValue);
                    log("写入库存: " + sku + " -> " + newValue);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        System.out.println("========== ReadWriteLock 最终快照 ==========");
        System.out.println("SKU-1001 = " + cache.get("SKU-1001"));
        System.out.println("SKU-1002 = " + cache.get("SKU-1002"));
    }

    /**
     * 简化版库存缓存。
     */
    private static class ProductCache {
        private final Map<String, Integer> stockMap = new HashMap<>();
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

        int get(String sku) {
            rwLock.readLock().lock();
            try {
                simulateCost(80);
                return stockMap.getOrDefault(sku, 0);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        void put(String sku, int stock) {
            rwLock.writeLock().lock();
            try {
                simulateCost(150);
                stockMap.put(sku, stock);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        private void simulateCost(int maxMs) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(20, maxMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}

