package com.example.multithread.lock;

import java.time.LocalTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 知识点：ReentrantLock（可重入锁）。
 *
 * 示例目标：
 * 1. 演示“可重入”：同一线程可重复获取同一把锁。
 * 2. 演示 tryLock(timeout) 在竞争激烈时的超时控制。
 * 3. 演示公平锁（fair=true）构造方式。
 *
 */
public class ReentrantLockAdvancedDemo {

    private static final ReentrantLock FAIR_LOCK = new ReentrantLock(true);

    public static void main(String[] args) throws InterruptedException {
        demoReentrancy();
        System.out.println();
        demoTryLockTimeout();
    }

    /**
     * 演示可重入：
     * 外层方法持锁后调用内层方法，内层方法可再次获取同一把锁。
     */
    private static void demoReentrancy() {
        System.out.println("========== ReentrantLock 可重入演示 ==========");
        outerMethod();
    }

    private static void outerMethod() {
        FAIR_LOCK.lock();
        try {
            log("进入 outerMethod，holdCount=" + FAIR_LOCK.getHoldCount());
            innerMethod();
        } finally {
            FAIR_LOCK.unlock();
            log("退出 outerMethod，holdCount=" + FAIR_LOCK.getHoldCount());
        }
    }

    private static void innerMethod() {
        FAIR_LOCK.lock();
        try {
            log("进入 innerMethod，holdCount=" + FAIR_LOCK.getHoldCount());
        } finally {
            FAIR_LOCK.unlock();
            log("退出 innerMethod，holdCount=" + FAIR_LOCK.getHoldCount());
        }
    }

    /**
     * 演示 tryLock(timeout)：
     * 线程 A 长时间占锁，线程 B 只等待有限时间，超时后快速失败并返回。
     */
    private static void demoTryLockTimeout() throws InterruptedException {
        System.out.println("========== ReentrantLock 超时获取演示 ==========");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            FAIR_LOCK.lock();
            try {
                log("线程A已持有锁，模拟执行耗时任务");
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                FAIR_LOCK.unlock();
                log("线程A释放锁");
            }
        });

        Thread.sleep(100); // 确保 A 先拿到锁

        executor.submit(() -> {
            try {
                log("线程B尝试在 300ms 内获取锁");
                boolean locked = FAIR_LOCK.tryLock(300, TimeUnit.MILLISECONDS);
                if (!locked) {
                    log("线程B获取锁超时，快速失败并走降级逻辑");
                    return;
                }
                try {
                    log("线程B获取锁成功，执行关键逻辑");
                } finally {
                    FAIR_LOCK.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("线程B等待锁被中断");
            }
        });

        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}

