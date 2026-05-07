package com.example.multithread.scheduled;

import java.time.LocalTime;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 生产场景示例：健康检查定时任务
 *
 * 场景说明：
 * - 服务通常需要周期性检查数据库、缓存、消息队列连通性
 * - 通过 ScheduledExecutorService 做固定频率巡检
 * - 程序退出时要优雅停机，避免任务突然中断
 */
public class HealthCheckSchedulerDemo {

    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> new Thread(r, "health-checker"));

        // 注册关闭钩子：模拟服务被停止时，确保调度线程池优雅退出
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownGracefully(scheduler), "shutdown-hook"));

        scheduler.scheduleAtFixedRate(() -> checkDependency("MySQL"), 0, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> checkDependency("Redis"), 0, 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> checkDependency("Kafka"), 0, 1, TimeUnit.SECONDS);

        // 示例运行几秒后主动结束
        Thread.sleep(3500);
        shutdownGracefully(scheduler);
        System.out.println("\n健康检查示例结束。");
    }

    private static void checkDependency(String dependency) {
        boolean healthy = RANDOM.nextInt(10) > 1; // 80% 健康
        String status = healthy ? "UP" : "DOWN";
        System.out.printf("%s [%s] %s status=%s%n",
                LocalTime.now(),
                Thread.currentThread().getName(),
                dependency,
                status);
    }

    private static void shutdownGracefully(ScheduledExecutorService scheduler) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }
}

