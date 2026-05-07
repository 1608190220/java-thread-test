package com.example.multithread.concurrentcollection.skiplists;

import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * 知识点：ConcurrentSkipListMap / ConcurrentSkipListSet。
 *
 * ConcurrentSkipListMap 和 ConcurrentSkipListSet 的共同特点：
 * 1. 线程安全。
 * 2. 按 key / 元素自然顺序排序，或按构造时传入的 Comparator 排序。
 * 3. 支持范围查询，例如 firstKey、lastKey、subMap、tailSet。
 *
 * 与 ConcurrentHashMap 的主要区别：
 * - ConcurrentHashMap 适合高吞吐 key-value 访问，但不维护顺序。
 * - ConcurrentSkipListMap 维护有序结构，适合排行榜、时间线、区间查询等场景。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.concurrentcollection.skiplists.ConcurrentSkipListCollectionDemo
 */
public class ConcurrentSkipListCollectionDemo {

    public static void main(String[] args) throws InterruptedException {
        demoSkipListMap();
        System.out.println();
        demoSkipListSet();
    }

    private static void demoSkipListMap() throws InterruptedException {
        ConcurrentSkipListMap<Integer, String> scoreBoard = new ConcurrentSkipListMap<>();

        Thread writerA = new Thread(() -> {
            scoreBoard.put(90, "Alice");
            scoreBoard.put(75, "Bob");
            log("writerA 写入两条成绩");
        }, "score-writer-A");

        Thread writerB = new Thread(() -> {
            scoreBoard.put(88, "Carol");
            scoreBoard.put(96, "David");
            log("writerB 写入两条成绩");
        }, "score-writer-B");

        writerA.start();
        writerB.start();
        writerA.join();
        writerB.join();

        log("最低分 = " + scoreBoard.firstEntry());
        log("最高分 = " + scoreBoard.lastEntry());
        log("80 到 95 分区间 = " + scoreBoard.subMap(80, true, 95, true));

        for (Map.Entry<Integer, String> entry : scoreBoard.entrySet()) {
            log("按分数升序遍历：" + entry.getKey() + " -> " + entry.getValue());
        }
    }

    private static void demoSkipListSet() throws InterruptedException {
        ConcurrentSkipListSet<String> onlineUsers = new ConcurrentSkipListSet<>();

        Thread regionA = new Thread(() -> {
            onlineUsers.add("user-1003");
            onlineUsers.add("user-1001");
            log("regionA 上报在线用户");
        }, "region-A");

        Thread regionB = new Thread(() -> {
            onlineUsers.add("user-1002");
            onlineUsers.add("user-1004");
            log("regionB 上报在线用户");
        }, "region-B");

        regionA.start();
        regionB.start();
        regionA.join();
        regionB.join();

        log("全部在线用户（自动排序）= " + onlineUsers);
        log("从 user-1002 开始的用户 = " + onlineUsers.tailSet("user-1002"));
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
