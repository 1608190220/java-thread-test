package com.example.multithread.forkjoin.workstealing;

import java.time.LocalTime;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * 知识点：工作窃取模型。
 *
 * ForkJoinPool 的核心思想：
 * 1. 每个工作线程都有自己的双端队列。
 * 2. 工作线程优先从自己的队列头部取任务，局部性更好。
 * 3. 当某个工作线程空闲时，会尝试从其他工作线程队列尾部“偷”任务执行。
 *
 * 本示例故意拆出很多小任务，并让不同小任务耗时不同，便于观察多个 worker 共同处理任务。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.forkjoin.workstealing.WorkStealingModelDemo
 */
public class WorkStealingModelDemo {

    public static void main(String[] args) {
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            pool.invoke(new UnevenWorkTask(1, 17));
            log("任务全部完成，stealCount=" + pool.getStealCount());
        }
    }

    private static class UnevenWorkTask extends RecursiveAction {
        private static final int THRESHOLD = 1;

        private final int start;
        private final int end;

        private UnevenWorkTask(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            if (end - start <= THRESHOLD) {
                doWork(start);
                return;
            }

            int middle = (start + end) / 2;
            invokeAll(new UnevenWorkTask(start, middle), new UnevenWorkTask(middle, end));
        }

        private void doWork(int taskNo) {
            try {
                // 让部分任务更慢，制造负载不均衡，空闲 worker 更容易触发窃取。
                int costMs = taskNo % 4 == 0 ? 450 : 120;
                Thread.sleep(costMs);
                log("完成小任务 taskNo=" + taskNo + ", costMs=" + costMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("任务被中断 taskNo=" + taskNo);
            }
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
