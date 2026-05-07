package com.example.multithread.memory.jmm;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;

/**
 * 知识点：happens-before / JMM（Java Memory Model）。
 *
 * JMM 关注的是多线程下“一个线程写入的值，另一个线程何时必须能看见”。
 *
 * happens-before 是 JMM 中非常关键的可见性规则：
 * 如果操作 A happens-before 操作 B，那么 A 的结果对 B 可见，并且 A 的执行顺序排在 B 之前。
 *
 * 本示例演示三条常用规则：
 * 1. volatile 写 happens-before 后续对同一个 volatile 变量的读。
 * 2. Thread.start() happens-before 新线程中的动作。
 * 3. 线程中的所有动作 happens-before 其他线程成功从 Thread.join() 返回。
 *
 * 注意：
 * - 本示例不通过“死循环卡住”来演示可见性问题，因为那类错误依赖 CPU、JIT 和时机，不适合作为稳定教学示例。
 * - 这里重点展示“正确建立 happens-before 后，读线程为什么能看见写线程结果”。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.memory.jmm.HappensBeforeJmmDemo
 */
public class HappensBeforeJmmDemo {

    private static int configVersion = 0;
    private static String configValue = "unset";
    private static volatile boolean configReady = false;

    private static int valueBeforeStart = 0;
    private static int workerResult = 0;

    public static void main(String[] args) throws InterruptedException {
        demoVolatileHappensBefore();
        System.out.println();
        demoThreadStartHappensBefore();
        System.out.println();
        demoThreadJoinHappensBefore();
    }

    /**
     * volatile 规则：
     * 写线程先写普通变量 configVersion / configValue，再写 volatile 变量 configReady=true。
     * 读线程读到 configReady=true 后，必须能看到 volatile 写之前的普通变量写入。
     */
    private static void demoVolatileHappensBefore() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(2);

        Thread writer = new Thread(() -> {
            configVersion = 1;
            configValue = "feature-x-enabled";

            // volatile 写：发布上面两个普通字段的写入结果。
            configReady = true;
            log("writer 发布配置完成");
            done.countDown();
        }, "jmm-writer");

        Thread reader = new Thread(() -> {
            while (!configReady) {
                Thread.onSpinWait();
            }

            // 读到 configReady=true 后，writer 在 volatile 写之前的普通写入对当前线程可见。
            log("reader 看到配置：version=" + configVersion + ", value=" + configValue);
            done.countDown();
        }, "jmm-reader");

        reader.start();
        writer.start();
        done.await();
    }

    /**
     * start 规则：
     * main 在线程 start() 之前写入 valueBeforeStart。
     * worker 启动后一定能看见 start() 之前已经发生的写入。
     */
    private static void demoThreadStartHappensBefore() throws InterruptedException {
        valueBeforeStart = 42;

        Thread worker = new Thread(() ->
                log("worker 启动后读取 start 前写入的值 valueBeforeStart=" + valueBeforeStart),
                "start-rule-worker"
        );

        worker.start();
        worker.join();
    }

    /**
     * join 规则：
     * worker 在线程内写入 workerResult。
     * main 成功从 worker.join() 返回后，一定能看见 worker 线程结束前的写入。
     */
    private static void demoThreadJoinHappensBefore() throws InterruptedException {
        Thread worker = new Thread(() -> {
            workerResult = 100;
            log("worker 写入 workerResult=100");
        }, "join-rule-worker");

        worker.start();
        worker.join();

        log("main 从 join 返回后读取 workerResult=" + workerResult);
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
