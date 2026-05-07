package com.example.multithread.atomic;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 综合示例：高并发指标统计（ConcurrentHashMap + LongAdder）。
 *
 * 场景说明：
 * 1. 统计接口总请求数、成功数、失败数。
 * 2. 按 API 维度统计调用量。
 * 3. 在高并发下使用 LongAdder 降低热点竞争。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.atomic.MetricsCounterDemo
 */
public class MetricsCounterDemo {

    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws InterruptedException {
        MetricsRegistry registry = new MetricsRegistry();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(200);

        List<String> apis = List.of("/api/order/create", "/api/order/cancel", "/api/order/query");

        for (int i = 0; i < 200; i++) {
            pool.submit(() -> {
                try {
                    String api = apis.get(RANDOM.nextInt(apis.size()));
                    boolean success = RANDOM.nextInt(10) > 1;

                    registry.increment("request.total");
                    registry.increment("request.api." + api);
                    registry.increment(success ? "request.success" : "request.fail");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println(LocalTime.now() + " 指标快照：");
        registry.snapshot().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println(entry.getKey() + " = " + entry.getValue()));
    }

    /**
     * 轻量指标注册中心：
     * - Key: 指标名；
     * - Value: LongAdder 计数器。
     */
    static class MetricsRegistry {
        private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

        public void increment(String metricName) {
            counters.computeIfAbsent(metricName, key -> new LongAdder()).increment();
        }

        public Map<String, Long> snapshot() {
            Map<String, Long> copy = new ConcurrentHashMap<>();
            counters.forEach((metric, adder) -> copy.put(metric, adder.sum()));
            return copy;
        }
    }
}