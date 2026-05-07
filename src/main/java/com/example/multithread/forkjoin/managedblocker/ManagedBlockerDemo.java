package com.example.multithread.forkjoin.managedblocker;

import java.time.LocalTime;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RecursiveAction;

/**
 * 知识点：阻塞任务配合 ForkJoinPool.ManagedBlocker。
 *
 * ForkJoinPool 更适合 CPU 密集型、可拆分的小任务。如果任务内部执行阻塞操作，
 * 工作线程会被占住，池的有效并行度会下降。
 *
 * ManagedBlocker 的作用：
 * 1. 告诉 ForkJoinPool：当前工作线程即将进入受控阻塞。
 * 2. ForkJoinPool 可以视情况临时补偿工作线程，减少阻塞对整体吞吐的影响。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.forkjoin.managedblocker.ManagedBlockerDemo
 */
public class ManagedBlockerDemo {

    public static void main(String[] args) throws InterruptedException {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();

        Thread producer = new Thread(() -> {
            sleep(600);
            queue.offer("库存服务返回：有货");
            log("生产者放入队列数据");
        }, "mock-remote-service");
        producer.start();

        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            pool.invoke(new QueueReadTask(queue));
        }

        producer.join();
    }

    private static class QueueReadTask extends RecursiveAction {
        private final LinkedBlockingQueue<String> queue;

        private QueueReadTask(LinkedBlockingQueue<String> queue) {
            this.queue = queue;
        }

        @Override
        protected void compute() {
            QueueTakeBlocker blocker = new QueueTakeBlocker(queue);
            try {
                log("准备从队列读取数据，队列为空时会阻塞");
                ForkJoinPool.managedBlock(blocker);
                log("读取到结果 = " + blocker.getValue());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("等待队列数据时被中断");
            }
        }
    }

    private static class QueueTakeBlocker implements ForkJoinPool.ManagedBlocker {
        private final LinkedBlockingQueue<String> queue;
        private String value;

        private QueueTakeBlocker(LinkedBlockingQueue<String> queue) {
            this.queue = queue;
        }

        @Override
        public boolean block() throws InterruptedException {
            if (value == null) {
                value = queue.take();
            }
            return true;
        }

        @Override
        public boolean isReleasable() {
            if (value != null) {
                return true;
            }

            // 先尝试无阻塞获取；如果能拿到数据，就不需要进入 block()。
            value = queue.poll();
            return value != null;
        }

        private String getValue() {
            return value;
        }
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
