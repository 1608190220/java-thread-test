package com.example.multithread.atomic.array;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 知识点：AtomicIntegerArray、AtomicLongArray、AtomicReferenceArray。
 *
 * 原子数组类解决的问题：
 * 1. 数组本身是共享对象，多个线程同时修改同一个下标时，普通数组会发生丢失更新。
 * 2. AtomicIntegerArray / AtomicLongArray 可以对“某一个下标”做原子加减、CAS 更新。
 * 3. AtomicReferenceArray 可以对“某一个下标上的引用”做原子替换，适合槽位状态、分片配置等场景。
 *
 * 注意：
 * - 原子数组保证的是单个元素的原子操作，不保证多个下标之间的复合操作天然原子。
 * - 构造 AtomicIntegerArray(int[]) 时会复制传入数组，后续修改原始数组不会影响原子数组。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.atomic.array.AtomicArrayDemo
 */
public class AtomicArrayDemo {

    public static void main(String[] args) throws InterruptedException {
        demoAtomicIntegerArray();
        System.out.println();
        demoAtomicLongArray();
        System.out.println();
        demoAtomicReferenceArray();
    }

    /**
     * AtomicIntegerArray 示例：
     * 多个线程并发统计每个接口的访问次数。
     *
     * 下标含义：
     * 0 -> /api/user
     * 1 -> /api/order
     * 2 -> /api/pay
     */
    private static void demoAtomicIntegerArray() throws InterruptedException {
        AtomicIntegerArray apiCounters = new AtomicIntegerArray(3);
        int threadCount = 4;
        int incrementsPerThread = 1_000;

        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        int apiIndex = j % apiCounters.length();

                        // incrementAndGet(index) 只对指定下标做原子自增。
                        // 多个线程同时更新同一个下标，也不会丢失更新。
                        apiCounters.incrementAndGet(apiIndex);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        shutdown(executor);

        log("========== AtomicIntegerArray 访问计数 ==========");
        log("/api/user  访问次数 = " + apiCounters.get(0));
        log("/api/order 访问次数 = " + apiCounters.get(1));
        log("/api/pay   访问次数 = " + apiCounters.get(2));
    }

    /**
     * AtomicLongArray 示例：
     * 多线程累加不同分片的订单金额。
     *
     * addAndGet(index, delta) 可以对某个下标做原子加法。
     */
    private static void demoAtomicLongArray() throws InterruptedException {
        AtomicLongArray shardAmounts = new AtomicLongArray(4);
        int threadCount = 4;

        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int workerNo = 0; workerNo < threadCount; workerNo++) {
            final int currentWorker = workerNo;
            executor.submit(() -> {
                try {
                    for (int orderNo = 1; orderNo <= 5; orderNo++) {
                        int shardIndex = (currentWorker + orderNo) % shardAmounts.length();
                        long amount = 100L + orderNo;

                        long after = shardAmounts.addAndGet(shardIndex, amount);
                        log("分片 " + shardIndex + " 累加订单金额 " + amount + " 后，总额=" + after);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        shutdown(executor);

        log("========== AtomicLongArray 分片金额 ==========");
        for (int i = 0; i < shardAmounts.length(); i++) {
            log("shard-" + i + " 金额合计 = " + shardAmounts.get(i));
        }
    }

    /**
     * AtomicReferenceArray 示例：
     * 多个线程尝试抢占任务槽位，把空槽位从 null 原子替换为 WorkerSlot。
     *
     * compareAndSet(index, expected, update) 可以保证：
     * - 只有当指定下标当前值还是 expected 时，才会替换为 update。
     * - 如果其他线程已经抢先写入，该 CAS 会失败。
     */
    private static void demoAtomicReferenceArray() throws InterruptedException {
        AtomicReferenceArray<WorkerSlot> slots = new AtomicReferenceArray<>(3);
        int workerCount = 6;

        CountDownLatch latch = new CountDownLatch(workerCount);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        for (int workerNo = 1; workerNo <= workerCount; workerNo++) {
            final String workerName = "worker-" + workerNo;
            executor.submit(() -> {
                try {
                    for (int slotIndex = 0; slotIndex < slots.length(); slotIndex++) {
                        WorkerSlot newSlot = new WorkerSlot(workerName, "TASK-" + slotIndex);

                        if (slots.compareAndSet(slotIndex, null, newSlot)) {
                            log(workerName + " 抢占槽位 " + slotIndex + " 成功");
                            return;
                        }
                    }
                    log(workerName + " 没有抢到空槽位");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        shutdown(executor);

        log("========== AtomicReferenceArray 槽位结果 ==========");
        for (int i = 0; i < slots.length(); i++) {
            log("slot-" + i + " = " + slots.get(i));
        }

        // 额外演示：AtomicIntegerArray 构造时会复制原数组，而不是直接持有原数组引用。
        int[] raw = {1, 2, 3};
        AtomicIntegerArray copied = new AtomicIntegerArray(raw);
        raw[0] = 99;
        log("原始数组修改后 raw=" + Arrays.toString(raw) + "，AtomicIntegerArray[0]=" + copied.get(0));
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    private static class WorkerSlot {
        private final String workerName;
        private final String taskId;

        private WorkerSlot(String workerName, String taskId) {
            this.workerName = workerName;
            this.taskId = taskId;
        }

        @Override
        public String toString() {
            return "WorkerSlot{workerName='" + workerName + "', taskId='" + taskId + "'}";
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
