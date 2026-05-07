package com.example.multithread.forkjoin.recursivetask;

import java.time.LocalTime;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * 知识点：RecursiveTask。
 *
 * RecursiveTask<V> 适合“需要返回结果”的分治任务，例如：
 * 1. 大数组求和。
 * 2. 递归计算最大值、最小值。
 * 3. 并行统计某段数据的指标。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.forkjoin.recursivetask.RecursiveTaskSumDemo
 */
public class RecursiveTaskSumDemo {

    public static void main(String[] args) {
        long[] orderAmounts = {
                99, 120, 880, 45, 330, 760, 210, 58,
                640, 75, 430, 510, 260, 30, 900, 150
        };

        try (ForkJoinPool pool = new ForkJoinPool()) {
            long total = pool.invoke(new OrderAmountSumTask(orderAmounts, 0, orderAmounts.length));
            log("订单总金额 = " + total);
        }
    }

    private static class OrderAmountSumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 4;

        private final long[] amounts;
        private final int start;
        private final int end;

        private OrderAmountSumTask(long[] amounts, int start, int end) {
            this.amounts = amounts;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            if (end - start <= THRESHOLD) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += amounts[i];
                }
                log("小任务直接汇总订单区间 [" + start + ", " + end + ")，金额=" + sum);
                return sum;
            }

            int middle = (start + end) / 2;
            OrderAmountSumTask left = new OrderAmountSumTask(amounts, start, middle);
            OrderAmountSumTask right = new OrderAmountSumTask(amounts, middle, end);

            // invokeAll 会同时安排两个子任务执行，当前任务随后 join 两边结果。
            invokeAll(left, right);
            long result = left.join() + right.join();
            log("合并订单区间 [" + start + ", " + end + ")，金额=" + result);
            return result;
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
