package com.example.multithread.concurrenttool;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 知识点：CountDownLatch（一次性协同器）。
 *
 * 示例目标：
 * 1. 演示"主流程等待多个子任务完成后再继续"。
 * 2. 演示 await(timeout) 避免无限等待。
 * 3. 理解 CountDownLatch 只能用一次，归零后不能重置。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.concurrenttool.CountDownLatchCoordinationDemo
 */
public class CountDownLatchCoordinationDemo {

    public static void main(String[] args) throws InterruptedException {
        List<String> modules = List.of("配置中心", "数据库连接池", "缓存客户端", "消息队列连接");
        CountDownLatch readyLatch = new CountDownLatch(modules.size());
        ExecutorService executor = Executors.newFixedThreadPool(modules.size());

        for (String module : modules) {
            executor.submit(() -> {
                try {
                    initModule(module);
                } finally {
                    readyLatch.countDown();
                    log(module + " 计数完成，剩余待完成=" + readyLatch.getCount());
                }
            });
        }

        log("主线程等待全部模块就绪...");
        boolean allReady = readyLatch.await(3, TimeUnit.SECONDS);

        if (allReady) {
            log("系统启动成功：全部模块就绪");
        } else {
            log("系统进入保护模式：初始化超时，拒绝外部流量");
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    private static void initModule(String module) {
        try {
            int cost = ThreadLocalRandom.current().nextInt(300, 1100);
            Thread.sleep(cost);
            log(module + " 初始化完成，耗时=" + cost + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log(module + " 初始化被中断");
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
