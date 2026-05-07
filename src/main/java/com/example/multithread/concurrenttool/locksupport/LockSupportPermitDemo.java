package com.example.multithread.concurrenttool.locksupport;

import java.time.LocalTime;
import java.util.concurrent.locks.LockSupport;

/**
 * 知识点：LockSupport。
 *
 * LockSupport 是很多 JUC 同步器的底层阻塞 / 唤醒工具，例如 AQS 内部就会用到它。
 *
 * 核心概念：permit（许可证）
 * 1. 每个线程最多持有一个 permit。
 * 2. unpark(thread) 会给目标线程发放一个 permit。
 * 3. park() 会消费一个 permit；如果没有 permit，当前线程阻塞。
 * 4. unpark 可以先于 park 调用。也就是说，先发 permit，后 park 时会立即返回。
 *
 * 与 wait / notify 的区别：
 * - wait / notify 必须配合 synchronized 监视器使用。
 * - LockSupport.park / unpark 不要求先获取某把锁。
 * - unpark 指定唤醒某一个线程，不像 notify 那样由 JVM 从等待集合里选择。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.concurrenttool.locksupport.LockSupportPermitDemo
 */
public class LockSupportPermitDemo {

    public static void main(String[] args) throws InterruptedException {
        demoUnparkBeforePark();
        System.out.println();
        demoParkThenUnpark();
        System.out.println();
        demoInterruptPark();
    }

    /**
     * 演示 unpark 可以先于 park：
     * main 线程先给 worker 发放 permit，worker 后续调用 park 时会直接返回。
     */
    private static void demoUnparkBeforePark() throws InterruptedException {
        Thread worker = new Thread(() -> {
            sleep(300);
            log("worker 准备 park。因为 permit 已经提前发放，所以不会阻塞");
            LockSupport.park("提前发放 permit 的演示");
            log("worker 从 park 返回");
        }, "unpark-before-park-worker");

        worker.start();
        log("main 在线程真正 park 前先 unpark(worker)");
        LockSupport.unpark(worker);
        worker.join();
    }

    /**
     * 演示 park 后由其他线程 unpark：
     * worker 没有 permit，会在 park 处阻塞，直到 main 调用 unpark。
     */
    private static void demoParkThenUnpark() throws InterruptedException {
        Thread worker = new Thread(() -> {
            log("worker 没有 permit，调用 park 后会阻塞");
            LockSupport.park("等待 main 发放 permit");
            log("worker 被 unpark 唤醒");
        }, "park-then-unpark-worker");

        worker.start();
        sleep(500);
        log("main 调用 unpark(worker)");
        LockSupport.unpark(worker);
        worker.join();
    }

    /**
     * 演示中断也会让 park 返回：
     * park 返回后要检查中断标记，否则业务代码可能误以为自己是被正常 unpark 唤醒。
     */
    private static void demoInterruptPark() throws InterruptedException {
        Thread worker = new Thread(() -> {
            log("worker 调用 park，稍后会被 interrupt");
            LockSupport.park("等待中断");
            log("park 返回，当前中断标记=" + Thread.currentThread().isInterrupted());
        }, "interrupt-park-worker");

        worker.start();
        sleep(500);
        log("main interrupt(worker)");
        worker.interrupt();
        worker.join();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
