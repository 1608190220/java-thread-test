package com.example.multithread.concurrenttool;

import java.time.LocalTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 知识点：Future（结果获取、超时控制、取消任务）。
 *
 * 示例目标：
 * 1. submit(Callable) 获取 Future。
 * 2. get(timeout) 防止主流程无限等待。
 * 3. 超时后 cancel(true) 中断任务，避免资源长时间占用。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.concurrenttool.FutureTimeoutCancelDemo
 */
public class FutureTimeoutCancelDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> {
                log("子任务开始：模拟调用慢接口（2.5 秒）");
                Thread.sleep(2500);
                return "慢接口结果：OK";
            });

            try {
                String result = future.get(1, TimeUnit.SECONDS);
                log("拿到结果：" + result);
            } catch (TimeoutException timeoutException) {
                log("等待超时，执行取消操作");
                boolean canceled = future.cancel(true);
                log("cancel(true) 返回=" + canceled);
            }

            // isDone: 任务是否已结束（成功、失败、取消都算结束）。
            // isCancelled: 任务是否被取消。
            log("最终状态：isDone=" + future.isDone() + ", isCancelled=" + future.isCancelled());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
