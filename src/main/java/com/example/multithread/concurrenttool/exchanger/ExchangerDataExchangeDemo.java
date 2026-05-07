package com.example.multithread.concurrenttool.exchanger;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 知识点：Exchanger。
 *
 * Exchanger 用于两个线程之间交换数据：
 * 1. 线程 A 调用 exchange(aData) 后会等待另一个线程。
 * 2. 线程 B 调用 exchange(bData) 后，A 得到 bData，B 得到 aData。
 * 3. 它只适合“两两配对交换”的场景，不适合多生产者多消费者队列场景。
 *
 * 本示例模拟“采集线程”和“处理线程”交换缓冲区：
 * - 采集线程把采集到的日志批次交给处理线程。
 * - 处理线程把已经清空的空缓冲区还给采集线程复用。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.concurrenttool.exchanger.ExchangerDataExchangeDemo
 */
public class ExchangerDataExchangeDemo {

    public static void main(String[] args) throws InterruptedException {
        Exchanger<List<String>> exchanger = new Exchanger<>();

        Thread producer = new Thread(() -> collectLogs(exchanger), "log-collector");
        Thread consumer = new Thread(() -> consumeLogs(exchanger), "log-consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();
        log("示例结束");
    }

    private static void collectLogs(Exchanger<List<String>> exchanger) {
        List<String> buffer = new ArrayList<>();
        for (int batchNo = 1; batchNo <= 3; batchNo++) {
            for (int i = 1; i <= 3; i++) {
                buffer.add("log-" + batchNo + "-" + i);
            }
            log("采集线程准备交换批次 " + batchNo + "，buffer=" + buffer);
            buffer = exchangeBuffer(exchanger, buffer);
            log("采集线程拿回空缓冲区，size=" + buffer.size());
        }
    }

    private static void consumeLogs(Exchanger<List<String>> exchanger) {
        List<String> emptyBuffer = new ArrayList<>();
        for (int batchNo = 1; batchNo <= 3; batchNo++) {
            List<String> received = exchangeBuffer(exchanger, emptyBuffer);
            log("处理线程收到批次 " + batchNo + "，received=" + received);

            // 模拟批量写入日志系统。处理完成后清空列表，下轮作为空缓冲区交还给采集线程。
            sleep(250);
            received.clear();
            emptyBuffer = received;
            log("处理线程清空缓冲区，准备下次交换");
        }
    }

    private static List<String> exchangeBuffer(Exchanger<List<String>> exchanger, List<String> buffer) {
        try {
            // 使用带超时的 exchange，避免配对线程异常退出后当前线程永久等待。
            return exchanger.exchange(buffer, 2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("交换时被中断，返回当前缓冲区");
            return buffer;
        } catch (TimeoutException e) {
            log("交换超时，返回当前缓冲区");
            return buffer;
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
