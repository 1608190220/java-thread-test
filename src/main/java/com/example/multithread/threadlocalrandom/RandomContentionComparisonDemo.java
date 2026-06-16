package com.example.multithread.threadlocalrandom;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 知识点：ThreadLocalRandom vs Random 在多线程下的竞争差异。
 *
 * 这个示例不是严格的 JMH 基准测试，只用于教学观察：
 * - 共享 Random：多个线程共同更新同一个随机种子，容易形成竞争点。
 * - ThreadLocalRandom：每个线程使用自己的随机状态，避免共享种子竞争。
 *
 * 如果要做严肃性能测试，应该使用 JMH，并隔离预热、JIT、GC、CPU 频率等因素。
 */
public class RandomContentionComparisonDemo {

    private static final int THREADS = Math.max(4, Runtime.getRuntime().availableProcessors());
    private static final int RANDOM_COUNT_PER_THREAD = 500_000;

    /**
     * 故意让所有工作线程共享同一个 Random，用来观察竞争。
     */
    private static final Random SHARED_RANDOM = new Random();

    public static void main(String[] args) throws Exception {
        log("===== Random 多线程竞争对比 =====");
        log("线程数：" + THREADS + "，每线程生成随机数次数：" + RANDOM_COUNT_PER_THREAD);

        // 先跑一轮较小的任务，让 JIT 有机会编译热点代码，减少首次执行带来的干扰。
        warmUp();

        Result sharedRandomResult = runScenario("共享 Random", () -> SHARED_RANDOM.nextInt(1_000));
        Result threadLocalRandomResult = runScenario("ThreadLocalRandom", () -> ThreadLocalRandom.current().nextInt(1_000));

        printResult(sharedRandomResult);
        printResult(threadLocalRandomResult);

        log("\n观察结论：");
        log("1. 共享 Random 的随机种子是共享状态，多线程同时 nextInt 时会争用同一个更新点。");
        log("2. ThreadLocalRandom 把随机状态分散到线程本地，通常更适合高并发随机数生成。");
        log("3. 如果你需要可复现的随机序列，例如单元测试固定结果，Random 显式传入 seed 仍然有价值。");
    }

    private static void warmUp() throws Exception {
        runScenario("预热-共享 Random", () -> SHARED_RANDOM.nextInt(1_000));
        runScenario("预热-ThreadLocalRandom", () -> ThreadLocalRandom.current().nextInt(1_000));
    }

    /**
     * 同时启动多个线程执行同一种随机数生成策略。
     *
     * CountDownLatch 的作用：
     * - readyLatch 确认所有任务都已经提交并准备好；
     * - startLatch 让所有任务尽量同时起跑，让竞争更容易被观察到。
     */
    private static Result runScenario(String name, RandomIntSource randomIntSource) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch readyLatch = new CountDownLatch(THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();

                    long checksum = 0;
                    for (int j = 0; j < RANDOM_COUNT_PER_THREAD; j++) {
                        // 累加到 checksum，避免 JIT 认为随机数结果完全没有用。
                        checksum += randomIntSource.nextInt();
                    }
                    return checksum;
                }));
            }

            readyLatch.await();
            long startNanos = System.nanoTime();
            startLatch.countDown();

            long checksum = 0;
            for (Future<Long> future : futures) {
                checksum += future.get();
            }
            long elapsedNanos = System.nanoTime() - startNanos;

            return new Result(name, elapsedNanos, checksum);
        } finally {
            pool.shutdown();
        }
    }

    private static void printResult(Result result) {
        long totalOperations = (long) THREADS * RANDOM_COUNT_PER_THREAD;
        double elapsedMs = result.elapsedNanos / 1_000_000.0;
        double operationsPerSecond = totalOperations * 1_000_000_000.0 / result.elapsedNanos;

        log(String.format(
                "%s：耗时 %.2fms，吞吐 %.0f ops/s，checksum=%d",
                result.name, elapsedMs, operationsPerSecond, result.checksum
        ));
    }

    @FunctionalInterface
    private interface RandomIntSource {
        int nextInt();
    }

    private record Result(String name, long elapsedNanos, long checksum) {
    }

    private static void log(String message) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), message);
    }
}
