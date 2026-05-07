package com.example.multithread.lock;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 知识点：synchronized 关键字（对象锁、类锁）。
 *
 * 示例目标：
 * 1. 在同样的并发计数任务下，对比不加锁与 synchronized 的结果差异。
 * 2. 演示 synchronized 的三种常见写法：实例方法、代码块、静态方法。
 * 3. 帮助理解“锁住的到底是谁”：
 *    - synchronized 实例方法：锁住当前对象实例（this）。
 *    - synchronized(某个对象)：锁住指定对象（monitor）。
 *    - static synchronized 方法：锁住类对象（Class）。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.lock.SynchronizedKeywordDemo
 */
public class SynchronizedKeywordDemo {

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 8;
        int loopPerThread = 20_000;
        int expected = threadCount * loopPerThread;

        System.out.println("========== synchronized 关键字演示 ==========");
        System.out.println("线程数 threadCount = " + threadCount);
        System.out.println("每线程循环 loopPerThread = " + loopPerThread);
        System.out.println("理论结果 expected = " + expected);
        System.out.println();

        // 对比 1：完全不加锁，通常会发生“丢失更新”。
        runCounterCase("不加锁（UnsafeCounter）", threadCount, loopPerThread, expected, new UnsafeCounter());

        // 对比 2：synchronized 实例方法，本质是对 this 加锁。
        runCounterCase("synchronized 实例方法（this 锁）", threadCount, loopPerThread, expected, new SynchronizedMethodCounter());

        // 对比 3：synchronized 代码块，可精确指定锁对象。
        runCounterCase("synchronized 代码块（自定义 monitor）", threadCount, loopPerThread, expected, new SynchronizedBlockCounter());

        // 对比 4：static synchronized 是类锁，锁对象为 Class；多个实例之间也会互斥。
        runClassLockCase(threadCount, loopPerThread, expected);
    }

    /**
     * 运行“共享同一个计数器实例”的并发测试。
     *
     * 实现思路：
     * 1. 创建固定大小线程池，保证并发量稳定。
     * 2. 每个线程对同一计数器执行相同次数的 increment。
     * 3. 通过 CountDownLatch 等待所有线程完成，再读取结果。
     */
    private static void runCounterCase(String caseName,
                                       int threadCount,
                                       int loopPerThread,
                                       int expected,
                                       Counter counter) throws InterruptedException {
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < loopPerThread; j++) {
                        counter.increment();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        int actual = counter.get();
        printResult(caseName, expected, actual);
    }

    /**
     * 单独演示 static synchronized（类锁）。
     *
     * 这里故意让每个工作线程都 new 一个独立实例，
     * 但它们调用的是同一个类锁保护的静态变量，最终仍能得到正确结果。
     */
    private static void runClassLockCase(int threadCount, int loopPerThread, int expected) throws InterruptedException {
        ClassLockCounter.reset();

        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 每个线程使用独立实例，验证“类锁”并不依赖某个具体实例。
                    ClassLockCounter localCounter = new ClassLockCounter();
                    for (int j = 0; j < loopPerThread; j++) {
                        localCounter.increment();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        int actual = ClassLockCounter.get();
        printResult("static synchronized 静态方法（Class 锁）", expected, actual);
        log("结论：synchronized 能保证临界区可见性与互斥性；不加锁时在并发下容易出现结果偏小。");
    }

    private static void printResult(String caseName, int expected, int actual) {
        System.out.println("---- " + caseName + " ----");
        System.out.println("理论值 expected = " + expected);
        System.out.println("实际值 actual    = " + actual);
        System.out.println(actual == expected ? "结果判定：正确（无丢失更新）" : "结果判定：错误（出现丢失更新）");
        System.out.println();
    }

    /**
     * 为了复用测试逻辑定义的统一计数器抽象。
     */
    private interface Counter {
        void increment();

        int get();
    }

    /**
     * 不加锁计数器：value++ 不是原子操作（读-改-写三个步骤）。
     */
    private static class UnsafeCounter implements Counter {
        private int value;

        @Override
        public void increment() {
            value++;
        }

        @Override
        public int get() {
            return value;
        }
    }

    /**
     * synchronized 实例方法：
     * - 锁对象是当前实例 this。
     * - 同一时刻只有一个线程能进入同一个实例的同步方法。
     */
    private static class SynchronizedMethodCounter implements Counter {
        private int value;

        @Override
        public synchronized void increment() {
            value++;
        }

        @Override
        public synchronized int get() {
            return value;
        }
    }

    /**
     * synchronized 代码块：
     * - 通过自定义 lock 对象明确“锁边界”。
     * - 实际业务中常用于只锁最小必要代码段，减少锁竞争。
     */
    private static class SynchronizedBlockCounter implements Counter {
        private final Object lock = new Object();
        private int value;

        @Override
        public void increment() {
            synchronized (lock) {
                value++;
            }
        }

        @Override
        public int get() {
            synchronized (lock) {
                return value;
            }
        }
    }

    /**
     * static synchronized：
     * - 锁对象是 Class 对象（ClassLockCounter.class）。
     * - 对同一个类的所有实例共享同一把锁。
     */
    private static class ClassLockCounter {
        private static int value;

        void increment() {
            incrementInternal();
        }

        static synchronized void incrementInternal() {
            value++;
        }

        static synchronized int get() {
            return value;
        }

        static synchronized void reset() {
            value = 0;
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
