package com.example.multithread.threadpool;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 知识点：ExecutorService 生命周期管理。
 *
 * 示例目标：
 * 1. 演示统一提交任务（execute / submit / invokeAll 的基础思路）。
 * 2. 演示线程池优雅关闭（shutdown + awaitTermination）。
 * 3. 演示超时后强制关闭（shutdownNow）作为兜底。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.threadpool.ExecutorServiceLifecycleDemo
 */
public class ExecutorServiceLifecycleDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            // 第 1 段：execute，适用于"不关心返回值"的任务。
            executor.execute(() -> log("execute 任务：异步写审计日志"));

            // 第 2 段：submit + Callable，适用于"需要返回值"的任务。
            String report = executor.submit(() -> {
                Thread.sleep(300);
                return "日报生成完成";
            }).get();
            log("submit 返回结果：" + report);

            // 第 3 段：invokeAll，批量提交并等待全部结束。
            List<Callable<String>> tasks = new ArrayList<>();
            tasks.add(() -> mockService("库存服务", 350));
            tasks.add(() -> mockService("价格服务", 420));
            tasks.add(() -> mockService("优惠服务", 280));

            List<String> results = executor.invokeAll(tasks)
                    .stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            return "任务失败：" + e.getMessage();
                        }
                    })
                    .toList();
            results.forEach(result -> log("invokeAll 结果：" + result));
        } finally {
            shutdownGracefully(executor);
        }
    }

    private static String mockService(String name, int costMs) throws InterruptedException {
        Thread.sleep(costMs);
        return name + " 完成，耗时=" + costMs + "ms";
    }

    private static void shutdownGracefully(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (executor.awaitTermination(2, TimeUnit.SECONDS)) {
            log("线程池已优雅关闭");
            return;
        }

        // 超时兜底：尝试中断正在执行的任务并返回未执行任务列表。
        List<Runnable> dropped = executor.shutdownNow();
        log("线程池超时，已强制关闭，未执行任务数=" + dropped.size());
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
