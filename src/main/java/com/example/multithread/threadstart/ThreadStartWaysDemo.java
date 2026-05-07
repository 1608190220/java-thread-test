package com.example.multithread.threadstart;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * 线程创建与任务执行方式示例：
 * 1) Thread（继承 Thread）
 * 2) Runnable（任务与线程分离）
 * 3) Callable（支持返回值与异常）
 * 4) FutureTask（可包装 Callable，并通过 get() 获取结果）
 * 5) 匿名内部类（补充示例）
 */
public class ThreadStartWaysDemo {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        System.out.println("=== 1) Thread（继承 Thread）===");
        startWithThreadSubclass();

        System.out.println("\n=== 2) Runnable + Thread ===");
        startWithRunnable();

        System.out.println("\n=== 3) Callable + Thread ===");
        startWithCallable();

        System.out.println("\n=== 4) FutureTask + Thread ===");
        startWithFutureTask();

        System.out.println("\n=== 5) 匿名内部类 ===");
        startWithAnonymousClass();
    }

    private static void startWithThreadSubclass() throws InterruptedException {
        // 方式一：继承 Thread，任务逻辑直接写在 run() 中
        Thread thread = new SimpleThreadTask("thread-subclass-worker");
        thread.start();
        thread.join();
    }

    private static void startWithRunnable() throws InterruptedException {
        // 方式二：实现 Runnable，任务与线程解耦，项目中更常用
        Runnable task = new SimpleRunnableTask("runnable-worker");
        Thread thread = new Thread(task, "runnable-thread");
        thread.start();
        thread.join();
    }

    private static void startWithCallable() throws InterruptedException, ExecutionException {
        // 方式三：Callable 支持返回值，也可以抛受检异常
        Callable<String> task = () -> {
            sleepQuietly(100);
            return "callable-result-from-" + Thread.currentThread().getName();
        };

        FutureTask<String> taskWrapper = new FutureTask<>(task);
        Thread thread = new Thread(taskWrapper, "callable-thread");
        thread.start();

        String result = taskWrapper.get();
        System.out.println("Callable 执行结果: " + result);
    }

    private static void startWithFutureTask() throws InterruptedException, ExecutionException {
        // 方式四：FutureTask 既是 Runnable 又是 Future，适合桥接任务执行与结果获取
        FutureTask<Integer> futureTask = new FutureTask<>(() -> {
            int sum = 0;
            for (int i = 1; i <= 5; i++) {
                sum += i;
                sleepQuietly(60);
            }
            return sum;
        });

        Thread thread = new Thread(futureTask, "future-task-thread");
        thread.start();

        Integer sumResult = futureTask.get();
        System.out.println("FutureTask 计算结果: " + sumResult);
    }

    private static void startWithAnonymousClass() throws InterruptedException {
        // 补充：匿名内部类，适合一次性、较短的任务
        Thread thread = new Thread("anonymous-thread") {
            @Override
            public void run() {
                for (int i = 1; i <= 3; i++) {
                    System.out.printf("[%s] anonymous run step %d%n", Thread.currentThread().getName(), i);
                    sleepQuietly(120);
                }
            }
        };
        thread.start();
        thread.join();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static class SimpleRunnableTask implements Runnable {
        private final String workerName;

        SimpleRunnableTask(String workerName) {
            this.workerName = workerName;
        }

        @Override
        public void run() {
            for (int i = 1; i <= 3; i++) {
                System.out.printf("[%s] %s run step %d%n", Thread.currentThread().getName(), workerName, i);
                sleepQuietly(100);
            }
        }
    }

    static class SimpleThreadTask extends Thread {
        private final String workerName;

        SimpleThreadTask(String workerName) {
            super("thread-subclass");
            this.workerName = workerName;
        }

        @Override
        public void run() {
            for (int i = 1; i <= 3; i++) {
                System.out.printf("[%s] %s run step %d%n", Thread.currentThread().getName(), workerName, i);
                sleepQuietly(110);
            }
        }
    }
}