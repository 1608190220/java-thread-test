package com.example.multithread.atomic.markable;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * 知识点：AtomicMarkableReference。
 *
 * AtomicMarkableReference<V> 可以把“引用”和“boolean 标记位”作为一个整体做原子更新。
 *
 * 常见用途：
 * 1. 无锁链表中，用 mark=true 表示节点已经被逻辑删除。
 * 2. 状态对象中，用 mark 表示是否有效、是否删除、是否冻结等二值状态。
 * 3. 缓解简单 ABA 问题：即使引用又变回旧值，标记位变化也能被 CAS 检测到。
 *
 * 与 AtomicStampedReference 的区别：
 * - AtomicStampedReference 携带 int stamp，适合记录版本号。
 * - AtomicMarkableReference 只携带 boolean mark，适合记录“是否删除”这类二值状态。
 *
 * 本示例模拟一个缓存项：
 * - reader 线程先读取缓存引用和 mark。
 * - deleter 线程把同一个缓存项标记为“已逻辑删除”。
 * - reader 再尝试基于旧 mark 更新缓存，会因为 mark 已变化而 CAS 失败。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.atomic.markable.AtomicMarkableReferenceDemo
 */
public class AtomicMarkableReferenceDemo {

    public static void main(String[] args) throws InterruptedException {
        CacheItem initialItem = new CacheItem("user:1001", "Alice");
        AtomicMarkableReference<CacheItem> cacheRef = new AtomicMarkableReference<>(initialItem, false);

        CountDownLatch readerHasRead = new CountDownLatch(1);
        CountDownLatch deleterDone = new CountDownLatch(1);

        Thread reader = new Thread(
                () -> readThenUpdate(cacheRef, readerHasRead, deleterDone),
                "cache-reader"
        );
        Thread deleter = new Thread(
                () -> markAsDeleted(cacheRef, readerHasRead, deleterDone),
                "cache-deleter"
        );

        reader.start();
        deleter.start();

        reader.join();
        deleter.join();

        boolean[] markHolder = new boolean[1];
        CacheItem finalItem = cacheRef.get(markHolder);
        log("最终缓存引用 = " + finalItem + "，mark(是否逻辑删除)=" + markHolder[0]);
    }

    private static void readThenUpdate(
            AtomicMarkableReference<CacheItem> cacheRef,
            CountDownLatch readerHasRead,
            CountDownLatch deleterDone
    ) {
        boolean[] markHolder = new boolean[1];
        CacheItem observedItem = cacheRef.get(markHolder);
        boolean observedMark = markHolder[0];

        log("reader 第一次读取：item=" + observedItem + "，mark=" + observedMark);
        readerHasRead.countDown();

        try {
            deleterDone.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("reader 等待删除线程时被中断");
            return;
        }

        CacheItem newItem = new CacheItem(observedItem.key, "Alice-updated");

        // 这里 expectedReference 仍然是 observedItem，expectedMark 仍然是 false。
        // deleter 已经把 mark 从 false 改成 true，所以即使引用没变，这次 CAS 也会失败。
        boolean updated = cacheRef.compareAndSet(
                observedItem,
                newItem,
                observedMark,
                false
        );

        log("reader 基于旧 mark 尝试更新，是否成功 = " + updated);
    }

    private static void markAsDeleted(
            AtomicMarkableReference<CacheItem> cacheRef,
            CountDownLatch readerHasRead,
            CountDownLatch deleterDone
    ) {
        try {
            readerHasRead.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("deleter 等待读线程时被中断");
            return;
        }

        boolean[] markHolder = new boolean[1];
        CacheItem currentItem = cacheRef.get(markHolder);

        // attemptMark 只修改 mark，不替换引用。
        // 它要求当前引用仍然等于 expectedReference，才能把 mark 改为 true。
        boolean marked = cacheRef.attemptMark(currentItem, true);
        log("deleter 逻辑删除缓存项，是否成功 = " + marked);

        deleterDone.countDown();
    }

    private static class CacheItem {
        private final String key;
        private final String value;

        private CacheItem(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return "CacheItem{key='" + key + "', value='" + value + "'}";
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
