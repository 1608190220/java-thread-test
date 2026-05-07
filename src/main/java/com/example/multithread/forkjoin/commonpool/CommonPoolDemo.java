package com.example.multithread.forkjoin.commonpool;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * 知识点：ForkJoinPool.commonPool()。
 *
 * commonPool 是 JDK 提供的全局共享 ForkJoinPool，很多 API 默认都会使用它，例如：
 * 1. CompletableFuture.supplyAsync(...) 不传 Executor 时。
 * 2. parallelStream()。
 *
 * 注意：
 * 1. commonPool 是全局共享资源，不适合放入长时间阻塞、不可控耗时的任务。
 * 2. 如果业务需要隔离资源，应该显式创建自己的线程池或 ForkJoinPool。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.forkjoin.commonpool.CommonPoolDemo
 */
public class CommonPoolDemo {

    public static void main(String[] args) {
        ForkJoinPool commonPool = ForkJoinPool.commonPool();
        log("commonPool parallelism = " + commonPool.getParallelism());

        CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> query("用户服务", 300));
        CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> query("订单服务", 450));
        CompletableFuture<String> couponFuture = CompletableFuture.supplyAsync(() -> query("优惠券服务", 200));

        List<String> results = CompletableFuture
                .allOf(userFuture, orderFuture, couponFuture)
                .thenApply(ignored -> List.of(userFuture.join(), orderFuture.join(), couponFuture.join()))
                .join();

        results.forEach(result -> log("聚合结果 = " + result));
    }

    private static String query(String serviceName, int costMs) {
        try {
            Thread.sleep(costMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return serviceName + " 被中断";
        }
        return serviceName + " 返回成功";
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
