package com.example.multithread.concurrentcollection.copyonwrite;

import java.time.LocalTime;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 知识点：CopyOnWriteArrayList / CopyOnWriteArraySet。
 *
 * CopyOnWrite 的核心思想：
 * 1. 读操作不加锁，直接读当前数组快照。
 * 2. 写操作会复制一份新数组，在新数组上修改，再把引用切换到新数组。
 * 3. 迭代器看到的是创建迭代器那一刻的快照，不会抛 ConcurrentModificationException。
 *
 * 适合场景：
 * - 读多写少，例如监听器列表、配置白名单、订阅者列表。
 *
 * 不适合场景：
 * - 写非常频繁或集合非常大，因为每次写都会复制数组。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.concurrentcollection.copyonwrite.CopyOnWriteCollectionDemo
 */
public class CopyOnWriteCollectionDemo {

    public static void main(String[] args) throws InterruptedException {
        demoCopyOnWriteArrayList();
        System.out.println();
        demoCopyOnWriteArraySet();
    }

    private static void demoCopyOnWriteArrayList() throws InterruptedException {
        CopyOnWriteArrayList<String> listeners = new CopyOnWriteArrayList<>();
        listeners.add("EmailListener");
        listeners.add("SmsListener");

        Iterator<String> snapshotIterator = listeners.iterator();

        Thread writer = new Thread(() -> {
            listeners.add("WebhookListener");
            listeners.remove("SmsListener");
            log("写线程修改监听器列表 = " + listeners);
        }, "listener-writer");

        writer.start();
        writer.join();

        log("主线程使用旧迭代器遍历快照：");
        while (snapshotIterator.hasNext()) {
            // 这里仍然能看到创建迭代器时的 SmsListener，看不到后来新增的 WebhookListener。
            log("快照元素 = " + snapshotIterator.next());
        }
        log("当前真实列表 = " + listeners);
    }

    private static void demoCopyOnWriteArraySet() throws InterruptedException {
        CopyOnWriteArraySet<String> featureFlags = new CopyOnWriteArraySet<>();
        featureFlags.add("new-homepage");
        featureFlags.add("fast-checkout");

        Thread writerA = new Thread(() -> {
            featureFlags.add("vip-price");
            featureFlags.add("new-homepage");
            log("writerA 更新开关集合");
        }, "flag-writer-A");

        Thread writerB = new Thread(() -> {
            featureFlags.add("gray-search");
            featureFlags.remove("fast-checkout");
            log("writerB 更新开关集合");
        }, "flag-writer-B");

        writerA.start();
        writerB.start();
        writerA.join();
        writerB.join();

        // Set 语义会去重，适合维护不重复的监听器、白名单、开关名等。
        log("当前功能开关集合 = " + featureFlags);
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
