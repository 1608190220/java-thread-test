package com.example.multithread.completablefuture;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 生产场景示例：多渠道比价聚合
 *
 * 场景说明：
 * - 一个请求需要并发调用多个下游渠道
 * - 每个渠道都要有超时兜底，防止拖慢整体请求
 * - 最终聚合结果可以取最优报价
 */
public class PriceComparisonDemo {

    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<String> channels = Arrays.asList("channel-A", "channel-B", "channel-C");

            List<CompletableFuture<Quote>> futures = channels.stream()
                    .map(channel -> CompletableFuture
                            .supplyAsync(() -> queryQuote(channel), executor)
                            // 渠道超时：返回降级结果，不阻塞主流程
                            .completeOnTimeout(Quote.timeout(channel), 700, TimeUnit.MILLISECONDS)
                            // 渠道异常：返回失败兜底，不抛到上层中断整个流程
                            .exceptionally(ex -> Quote.error(channel, ex.getMessage())))
                    .toList();

            CompletableFuture<Void> allDone = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            allDone.join();

            List<Quote> quotes = futures.stream().map(CompletableFuture::join).toList();
            quotes.forEach(quote -> System.out.println(LocalTime.now() + " " + quote));

            Quote best = quotes.stream()
                    .filter(quote -> quote.status == QuoteStatus.SUCCESS)
                    .min((q1, q2) -> q1.price.compareTo(q2.price))
                    .orElse(Quote.error("N/A", "没有可用报价"));

            System.out.println("\n聚合结果：最优报价 -> " + best);
        } finally {
            executor.shutdown();
        }
    }

    private static Quote queryQuote(String channel) {
        try {
            // 模拟不同渠道响应时延
            int delay = 200 + RANDOM.nextInt(700);
            Thread.sleep(delay);

            // 模拟某些情况下渠道返回错误
            if ("channel-B".equals(channel) && RANDOM.nextBoolean()) {
                throw new RuntimeException("下游返回 5xx");
            }

            BigDecimal price = BigDecimal.valueOf(95 + RANDOM.nextInt(20));
            return Quote.success(channel, price, delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Quote.error(channel, "线程中断");
        }
    }

    enum QuoteStatus {
        SUCCESS, TIMEOUT, ERROR
    }

    static class Quote {
        private final String channel;
        private final BigDecimal price;
        private final int costMs;
        private final QuoteStatus status;
        private final String message;

        private Quote(String channel, BigDecimal price, int costMs, QuoteStatus status, String message) {
            this.channel = channel;
            this.price = price;
            this.costMs = costMs;
            this.status = status;
            this.message = message;
        }

        static Quote success(String channel, BigDecimal price, int costMs) {
            return new Quote(channel, price, costMs, QuoteStatus.SUCCESS, "OK");
        }

        static Quote timeout(String channel) {
            return new Quote(channel, BigDecimal.valueOf(Double.MAX_VALUE), 700, QuoteStatus.TIMEOUT, "超时降级");
        }

        static Quote error(String channel, String message) {
            return new Quote(channel, BigDecimal.valueOf(Double.MAX_VALUE), -1, QuoteStatus.ERROR, message);
        }

        @Override
        public String toString() {
            return String.format("Quote{channel='%s', price=%s, costMs=%d, status=%s, message='%s'}",
                    channel, price, costMs, status, message);
        }
    }
}

