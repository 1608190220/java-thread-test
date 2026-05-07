package com.example.multithread.atomic.accumulator;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAccumulator;

/**
 * 知识点：LongAccumulator、DoubleAdder、DoubleAccumulator。
 *
 * 它们和 LongAdder 的思路类似：在高并发更新时，把热点拆散到多个内部单元中，
 * 最后再把多个单元聚合起来，从而降低单个 CAS 变量上的竞争。
 *
 * 适用场景：
 * 1. DoubleAdder：高并发累加 double，例如统计总耗时、总金额、总评分。
 * 2. LongAccumulator：自定义 long 聚合函数，例如最大值、最小值、按位聚合。
 * 3. DoubleAccumulator：自定义 double 聚合函数，例如最大响应时间、最低价格。
 *
 * 注意：
 * - sum() / get() 是当前时刻的聚合快照，并不是和所有并发写入形成一个全局互斥事务。
 * - 这类累加器适合统计指标，不适合需要严格线性化读取的账户余额、库存扣减等场景。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.atomic.accumulator.AccumulatorDemo
 */
public class AccumulatorDemo {

    public static void main(String[] args) throws InterruptedException {
        demoDoubleAdder();
        System.out.println();
        demoLongAccumulator();
        System.out.println();
        demoDoubleAccumulator();
    }

    /**
     * DoubleAdder 示例：
     * 多线程累加接口耗时，最后计算平均耗时。
     */
    private static void demoDoubleAdder() throws InterruptedException {
        DoubleAdder totalLatencyMs = new DoubleAdder();
        LongAccumulator requestCount = new LongAccumulator(Long::sum, 0);
        List<Double> latencies = List.of(12.5, 18.0, 9.5, 31.0, 22.5, 17.5, 14.0, 40.0);

        runInParallel(latencies, latency -> {
            totalLatencyMs.add(latency);
            requestCount.accumulate(1);
            log("记录一次接口耗时 latencyMs=" + latency);
        });

        double total = totalLatencyMs.sum();
        long count = requestCount.get();
        log("========== DoubleAdder 总耗时统计 ==========");
        log("请求数=" + count + "，总耗时=" + total + "ms，平均耗时=" + total / count + "ms");
    }

    /**
     * LongAccumulator 示例：
     * 用自定义聚合函数 Long::max 统计最大订单金额。
     *
     * identity 是聚合初始值。统计最大值时，如果业务金额都为正数，可以用 0。
     */
    private static void demoLongAccumulator() throws InterruptedException {
        LongAccumulator maxOrderAmount = new LongAccumulator(Long::max, 0);
        List<Long> orderAmounts = List.of(199L, 880L, 45L, 1200L, 760L, 330L);

        runInParallel(orderAmounts, amount -> {
            maxOrderAmount.accumulate(amount);
            log("上报订单金额 amount=" + amount);
        });

        log("========== LongAccumulator 最大值统计 ==========");
        log("最大订单金额=" + maxOrderAmount.get());
    }

    /**
     * DoubleAccumulator 示例：
     * 用自定义聚合函数 Double::min 统计最低报价。
     *
     * identity 要选择不会影响聚合结果的初始值。
     * 统计最小值时，用 Double.POSITIVE_INFINITY 比用 0 更合适。
     */
    private static void demoDoubleAccumulator() throws InterruptedException {
        DoubleAccumulator minQuote = new DoubleAccumulator(Double::min, Double.POSITIVE_INFINITY);
        List<Double> quotes = List.of(102.5, 99.9, 108.0, 101.2, 98.8);

        runInParallel(quotes, quote -> {
            minQuote.accumulate(quote);
            log("收到供应商报价 quote=" + quote);
        });

        log("========== DoubleAccumulator 最小值统计 ==========");
        log("最低报价=" + minQuote.get());
    }

    private static <T> void runInParallel(List<T> values, ValueHandler<T> handler) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(values.size());
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(values.size(), 4));
        for (T value : values) {
            executor.submit(() -> {
                try {
                    handler.handle(value);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ValueHandler<T> {
        void handle(T value);
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
