package com.example.multithread.concurrenttool;

import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 生产场景示例：系统模块并行启动
 *
 * 场景说明：
 * - 服务启动时要初始化多个模块（配置中心、数据库连接池、缓存、消息中间件）
 * - 这些初始化可以并行执行，全部就绪后再对外提供服务
 */
public class CountDownLatchStartupDemo {

    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws InterruptedException {
        List<String> modules = List.of("ConfigCenter", "MySQLPool", "RedisClient", "KafkaProducer");
        CountDownLatch readyLatch = new CountDownLatch(modules.size());
        ExecutorService executor = Executors.newFixedThreadPool(modules.size());

        for (String module : modules) {
            executor.submit(() -> {
                try {
                    initModule(module);
                } finally {
                    readyLatch.countDown();
                }
            });
        }

        System.out.println("主线程等待模块初始化完成...");
        boolean started = readyLatch.await(4, TimeUnit.SECONDS);

        if (started) {
            System.out.println("所有模块已就绪，系统开始对外提供服务。");
        } else {
            System.out.println("模块初始化超时，系统进入保护模式（拒绝外部流量）。");
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    private static void initModule(String module) {
        try {
            int cost = 400 + RANDOM.nextInt(900);
            Thread.sleep(cost);
            System.out.printf("%s [%s] 模块 %s 初始化完成，耗时=%dms%n",
                    LocalTime.now(), Thread.currentThread().getName(), module, cost);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.printf("模块 %s 初始化被中断%n", module);
        }
    }
}
