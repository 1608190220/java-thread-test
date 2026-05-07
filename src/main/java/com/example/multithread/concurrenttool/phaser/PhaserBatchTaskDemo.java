package com.example.multithread.concurrenttool.phaser;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Phaser;

/**
 * 知识点：Phaser。
 *
 * Phaser 可以理解为“更灵活的 CyclicBarrier / CountDownLatch”：
 * 1. 它支持多个阶段（phase），每一阶段都可以等待一批线程到齐后再进入下一阶段。
 * 2. 它支持动态注册和注销参与方，不要求一开始就固定线程数量。
 * 3. 它适合多阶段流水线，例如：加载数据 -> 校验数据 -> 写入数据。
 *
 * 本示例模拟 3 个数据分片，所有分片都必须按阶段推进：
 * 第 0 阶段：加载数据。
 * 第 1 阶段：校验数据。
 * 第 2 阶段：写入数据。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.concurrenttool.phaser.PhaserBatchTaskDemo
 */
public class PhaserBatchTaskDemo {

    public static void main(String[] args) {
        List<String> shards = List.of("分片-A", "分片-B", "分片-C");

        // 主线程也注册为一个参与方，目的是控制所有工作线程启动完毕后，再统一放行第 0 阶段。
        Phaser phaser = new LoggingPhaser(1);

        for (String shard : shards) {
            phaser.register();
            Thread worker = new Thread(() -> processShard(phaser, shard), "worker-" + shard);
            worker.start();
        }

        log("所有分片线程已创建，主线程到达第 0 阶段屏障");
        phaser.arriveAndDeregister();
    }

    private static void processShard(Phaser phaser, String shard) {
        try {
            log(shard + " 等待统一开始");
            phaser.arriveAndAwaitAdvance();

            loadData(shard);
            phaser.arriveAndAwaitAdvance();

            validateData(shard);
            phaser.arriveAndAwaitAdvance();

            writeData(shard);
            phaser.arriveAndAwaitAdvance();
        } finally {
            // 当前分片已经完成所有阶段，注销参与方，避免 Phaser 永远等待这个线程。
            phaser.arriveAndDeregister();
            log(shard + " 完成全部阶段并注销");
        }
    }

    private static void loadData(String shard) {
        sleep(200);
        log(shard + " 加载数据完成");
    }

    private static void validateData(String shard) {
        sleep(300);
        log(shard + " 校验数据完成");
    }

    private static void writeData(String shard) {
        sleep(150);
        log(shard + " 写入数据完成");
    }

    /**
     * 通过重写 onAdvance 观察每一阶段结束的时机。
     * 返回 true 表示终止 Phaser；返回 false 表示继续进入下一阶段。
     */
    private static class LoggingPhaser extends Phaser {
        private LoggingPhaser(int parties) {
            super(parties);
        }

        @Override
        protected boolean onAdvance(int phase, int registeredParties) {
            log("阶段 " + phase + " 已结束，剩余参与方数量=" + registeredParties);
            return registeredParties == 0 || phase >= 3;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("线程被中断");
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
