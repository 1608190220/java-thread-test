package com.example.multithread.forkjoin.countedcompleter;

import java.time.LocalTime;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 知识点：CountedCompleter。
 *
 * CountedCompleter 是 ForkJoinTask 的高级形态，适合这类场景：
 * 1. 子任务之间不需要通过 join 逐个返回结果。
 * 2. 任务完成动作依赖“还有多少子任务未完成”这个计数。
 * 3. 可以用 complete(...) 提前结束整棵任务树，例如搜索到目标后立即完成根任务。
 *
 * 本示例在商品编号数组中并行搜索目标编号。找到后，子任务把结果写入 AtomicReference，
 * 并调用 root.complete(null) 通知根任务提前完成。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.forkjoin.countedcompleter.CountedCompleterSearchDemo
 */
public class CountedCompleterSearchDemo {

    public static void main(String[] args) {
        String[] productCodes = {
                "SKU-1001", "SKU-1002", "SKU-1003", "SKU-1004",
                "SKU-2001", "SKU-2002", "SKU-2003", "SKU-2004",
                "SKU-3001", "SKU-3002", "SKU-3003", "SKU-3004"
        };

        AtomicReference<String> result = new AtomicReference<>();
        SearchTask root = new SearchTask(null, productCodes, "SKU-2003", 0, productCodes.length, result);

        ForkJoinPool.commonPool().invoke(root);
        log("搜索结果 = " + result.get());
    }

    private static class SearchTask extends CountedCompleter<Void> {
        private static final int THRESHOLD = 3;

        private final String[] productCodes;
        private final String target;
        private final int start;
        private final int end;
        private final AtomicReference<String> result;

        private SearchTask(
                CountedCompleter<?> parent,
                String[] productCodes,
                String target,
                int start,
                int end,
                AtomicReference<String> result
        ) {
            super(parent);
            this.productCodes = productCodes;
            this.target = target;
            this.start = start;
            this.end = end;
            this.result = result;
        }

        @Override
        public void compute() {
            // 如果其他任务已经找到结果，当前任务可以直接结束，避免继续做无意义扫描。
            if (result.get() != null) {
                tryComplete();
                return;
            }

            if (end - start <= THRESHOLD) {
                scanDirectly();
                tryComplete();
                return;
            }

            int middle = (start + end) / 2;
            SearchTask left = new SearchTask(this, productCodes, target, start, middle, result);
            SearchTask right = new SearchTask(this, productCodes, target, middle, end, result);

            // pending count 表示当前任务还需要等待多少个“异步 fork 出去的子任务”完成。
            // 这里只 fork right，left 由当前线程继续计算，所以等待计数设置为 1。
            // 当 left 和 right 都 tryComplete 后，父任务会继续向上完成。
            setPendingCount(1);
            right.fork();
            left.compute();
        }

        private void scanDirectly() {
            log("扫描区间 [" + start + ", " + end + ")");
            for (int i = start; i < end; i++) {
                if (target.equals(productCodes[i]) && result.compareAndSet(null, productCodes[i])) {
                    log("找到目标商品 = " + productCodes[i]);
                    // complete 会让根任务尽快完成。已经开始运行的其他子任务不会被强制中断，
                    // 但它们会在下一次检查 result 时尽早返回。
                    getRoot().complete(null);
                    return;
                }
            }
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
