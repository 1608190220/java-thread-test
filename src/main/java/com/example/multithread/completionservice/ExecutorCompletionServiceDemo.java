package com.example.multithread.completionservice;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 知识点：ExecutorCompletionService。
 *
 * ExecutorCompletionService = Executor + BlockingQueue<Future>。
 *
 * 它解决的问题：
 * 1. 批量提交多个 Callable。
 * 2. 谁先执行完成，就先从完成队列里取谁的 Future。
 * 3. 不必按提交顺序逐个 future.get()，避免慢任务阻塞快任务结果的消费。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.completionservice.ExecutorCompletionServiceDemo
 */
public class ExecutorCompletionServiceDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        ExecutorCompletionService<String> completionService = new ExecutorCompletionService<>(executor);

        List<Callable<String>> tasks = List.of(
                () -> queryVendor("供应商A", 700, 108),
                () -> queryVendor("供应商B", 300, 115),
                () -> queryVendor("供应商C", 500, 102)
        );

        try {
            for (Callable<String> task : tasks) {
                completionService.submit(task);
            }

            for (int i = 0; i < tasks.size(); i++) {
                // take() 会阻塞直到“任意一个任务”完成。
                // 返回顺序由完成时间决定，不由提交顺序决定。
                Future<String> completedFuture = completionService.take();
                log("收到已完成任务结果 = " + completedFuture.get());
            }
        } finally {
            executor.shutdown();
        }
    }

    private static String queryVendor(String vendorName, int costMs, int price) throws InterruptedException {
        log(vendorName + " 开始报价");
        Thread.sleep(costMs);
        return vendorName + " 报价=" + price + "，耗时=" + costMs + "ms";
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
