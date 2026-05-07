package com.example.multithread.completablefuture;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 知识点：CompletableFuture（异步编排与结果合并）。
 *
 * 示例目标：
 * 1. supplyAsync 并发发起多个异步任务。
 * 2. thenCombine 合并多个结果，构建业务流水线。
 * 3. completeOnTimeout + exceptionally 做超时与异常兜底。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.completablefuture.CompletableFuturePipelineDemo
 */
public class CompletableFuturePipelineDemo {

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            CompletableFuture<BigDecimal> priceFuture = CompletableFuture
                    .supplyAsync(() -> queryBasePrice("SKU-1001"), pool)
                    // 当上游慢于 800ms 时，降级为默认价格，避免主流程无限等待。
                    .completeOnTimeout(BigDecimal.valueOf(199), 800, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log("价格服务异常，降级默认价：" + ex.getMessage());
                        return BigDecimal.valueOf(199);
                    });

            CompletableFuture<BigDecimal> discountFuture = CompletableFuture
                    .supplyAsync(() -> queryDiscount("VIP-A"), pool)
                    .completeOnTimeout(BigDecimal.ZERO, 800, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log("优惠服务异常，降级无优惠：" + ex.getMessage());
                        return BigDecimal.ZERO;
                    });

            CompletableFuture<BigDecimal> finalPriceFuture = priceFuture.thenCombine(
                    discountFuture,
                    (price, discount) -> price.subtract(discount).max(BigDecimal.ONE)
            );

            BigDecimal finalPrice = finalPriceFuture.join();
            log("最终成交价 = " + finalPrice);
        } finally {
            pool.shutdown();
        }
    }

    private static BigDecimal queryBasePrice(String skuId) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        BigDecimal result = BigDecimal.valueOf(299);
        log("价格服务返回：sku=" + skuId + ", price=" + result);
        return result;
    }

    private static BigDecimal queryDiscount(String vipLevel) {
        try {
            Thread.sleep(350);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        BigDecimal result = BigDecimal.valueOf(60);
        log("优惠服务返回：vip=" + vipLevel + ", discount=" + result);
        return result;
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
