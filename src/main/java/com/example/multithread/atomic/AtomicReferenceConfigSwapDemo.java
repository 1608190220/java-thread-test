package com.example.multithread.atomic;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 知识点：AtomicReference（无锁原子引用）。
 *
 * 示例场景：配置中心热更新（CAS 方式）。
 *
 * 示例目标：
 * 1. 用不可变对象 + AtomicReference 管理共享配置。
 * 2. 用 compareAndSet 保证“读取旧值 -> 基于旧值更新”的原子性。
 * 3. 演示并发更新时的 CAS 重试思路。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.atomic.AtomicReferenceConfigSwapDemo
 */
public class AtomicReferenceConfigSwapDemo {

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<AppConfig> configRef = new AtomicReference<>(
                new AppConfig(1, "https://api-v1.example.com", 1500)
        );

        int updaterThreads = 3;
        CountDownLatch doneLatch = new CountDownLatch(updaterThreads);
        ExecutorService executor = Executors.newFixedThreadPool(updaterThreads);

        // 三个“运维动作”并发发生：切换 endpoint / 调整超时时间 / 再次切换 endpoint
        executor.submit(() -> updateEndpoint(configRef, "https://api-v2.example.com", doneLatch));
        executor.submit(() -> updateTimeout(configRef, 2200, doneLatch));
        executor.submit(() -> updateEndpoint(configRef, "https://api-gray.example.com", doneLatch));

        doneLatch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        System.out.println("========== AtomicReference 配置热更新演示 ==========");
        System.out.println("最终配置 = " + configRef.get());
        log("结论：AtomicReference 适合管理“整体替换”的共享对象。");
    }

    private static void updateEndpoint(
            AtomicReference<AppConfig> configRef,
            String newEndpoint,
            CountDownLatch doneLatch
    ) {
        try {
            while (true) {
                AppConfig oldConfig = configRef.get();
                AppConfig newConfig = new AppConfig(
                        oldConfig.version + 1,
                        newEndpoint,
                        oldConfig.timeoutMs
                );

                if (configRef.compareAndSet(oldConfig, newConfig)) {
                    log("更新 endpoint 成功: " + oldConfig.endpoint + " -> " + newEndpoint);
                    return;
                }

                // CAS 失败表示期间有其他线程先完成了更新，循环重试即可。
                log("更新 endpoint CAS 失败，准备重试");
            }
        } finally {
            doneLatch.countDown();
        }
    }

    private static void updateTimeout(
            AtomicReference<AppConfig> configRef,
            int timeoutMs,
            CountDownLatch doneLatch
    ) {
        try {
            while (true) {
                AppConfig oldConfig = configRef.get();
                AppConfig newConfig = new AppConfig(
                        oldConfig.version + 1,
                        oldConfig.endpoint,
                        timeoutMs
                );

                if (configRef.compareAndSet(oldConfig, newConfig)) {
                    log("更新 timeout 成功: " + oldConfig.timeoutMs + " -> " + timeoutMs);
                    return;
                }

                log("更新 timeout CAS 失败，准备重试");
            }
        } finally {
            doneLatch.countDown();
        }
    }

    /**
     * 不可变配置对象：
     * - 字段 final，创建后不再修改；
     * - 更新配置时创建新对象，再原子替换引用。
     */
    private static class AppConfig {
        private final int version;
        private final String endpoint;
        private final int timeoutMs;

        private AppConfig(int version, String endpoint, int timeoutMs) {
            this.version = version;
            this.endpoint = endpoint;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public String toString() {
            return String.format("AppConfig{version=%d, endpoint='%s', timeoutMs=%d}",
                    version, endpoint, timeoutMs);
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}

