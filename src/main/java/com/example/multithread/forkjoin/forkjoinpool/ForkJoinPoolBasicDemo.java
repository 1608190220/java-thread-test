package com.example.multithread.forkjoin.forkjoinpool;

import java.time.LocalTime;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * 知识点：ForkJoinPool 基础用法。
 *
 * 示例目标：
 * 1. 认识 ForkJoinPool 是专门服务于“可拆分任务”的线程池。
 * 2. 观察 fork / join 如何把大任务拆成小任务，再把小任务结果合并回来。
 * 3. 观察自定义 ForkJoinPool 与普通 fixed thread pool 的区别：ForkJoinPool 的工作线程会维护自己的双端队列。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.forkjoin.forkjoinpool.ForkJoinPoolBasicDemo
 */
public class ForkJoinPoolBasicDemo {

    public static void main(String[] args) {
        int[] numbers = createNumbers(1, 100);

        // parallelism 表示 ForkJoinPool 期望同时活跃的工作线程数量。
        // CPU 密集型任务通常设置为 CPU 核心数附近；这里为了方便观察日志，固定为 4。
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            int result = pool.invoke(new SumTask(numbers, 0, numbers.length));
            log("最终求和结果 = " + result);
            log("池并行度 parallelism = " + pool.getParallelism());
            log("窃取任务次数 stealCount = " + pool.getStealCount());
        }
    }

    private static int[] createNumbers(int startInclusive, int endInclusive) {
        int[] numbers = new int[endInclusive - startInclusive + 1];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = startInclusive + i;
        }
        return numbers;
    }

    private static class SumTask extends RecursiveTask<Integer> {
        private static final int THRESHOLD = 20;

        private final int[] numbers;
        private final int start;
        private final int end;

        private SumTask(int[] numbers, int start, int end) {
            this.numbers = numbers;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Integer compute() {
            int length = end - start;
            if (length <= THRESHOLD) {
                int sum = 0;
                for (int i = start; i < end; i++) {
                    sum += numbers[i];
                }
                log("直接计算区间 [" + start + ", " + end + ")，sum=" + sum);
                return sum;
            }

            int middle = start + length / 2;
            SumTask left = new SumTask(numbers, start, middle);
            SumTask right = new SumTask(numbers, middle, end);

            // fork 表示把子任务放入当前工作线程的双端队列，等待当前线程或其他工作线程执行。
            left.fork();

            // 通常让当前线程直接计算其中一个子任务，减少一次调度成本。
            int rightResult = right.compute();

            // join 等待 fork 出去的子任务完成，并取得返回值。
            int leftResult = left.join();
            int merged = leftResult + rightResult;
            log("合并区间 [" + start + ", " + end + ")，sum=" + merged);
            return merged;
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
