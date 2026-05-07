package com.example.multithread.flow.submissionpublisher;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

/**
 * 知识点：Flow / SubmissionPublisher。
 *
 * Flow 是 JDK 9 引入的响应式流接口，核心角色：
 * 1. Publisher：发布数据。
 * 2. Subscriber：订阅并消费数据。
 * 3. Subscription：订阅关系，Subscriber 通过它向 Publisher 请求数据或取消订阅。
 * 4. Processor：既是 Subscriber 又是 Publisher，可用于中间转换。本示例只演示 Publisher / Subscriber。
 *
 * SubmissionPublisher 是 JDK 提供的一个基础 Publisher 实现：
 * - submit(item) 发布数据。
 * - close() 表示发布完成。
 * - Subscriber 必须调用 subscription.request(n) 请求数据，否则不会收到 onNext。
 *
 * 本示例模拟订单事件发布：
 * - publisher 发布 5 个订单事件。
 * - subscriber 每次只 request(1)，处理完一个再请求下一个，用来观察背压思想。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.flow.submissionpublisher.SubmissionPublisherFlowDemo
 */
public class SubmissionPublisherFlowDemo {

    public static void main(String[] args) throws InterruptedException {
        try (SubmissionPublisher<OrderEvent> publisher = new SubmissionPublisher<>()) {
            OrderEventSubscriber subscriber = new OrderEventSubscriber("order-audit-subscriber");
            publisher.subscribe(subscriber);

            List<OrderEvent> events = List.of(
                    new OrderEvent("ORDER-1001", "CREATED"),
                    new OrderEvent("ORDER-1002", "PAID"),
                    new OrderEvent("ORDER-1003", "CANCELLED"),
                    new OrderEvent("ORDER-1004", "SHIPPED"),
                    new OrderEvent("ORDER-1005", "FINISHED")
            );

            for (OrderEvent event : events) {
                log("publisher 发布事件 " + event);
                int estimatedLag = publisher.submit(event);
                log("publisher 当前估算积压数量 estimatedLag=" + estimatedLag);
            }

            log("publisher close，表示不再发布新事件");
        }

        // SubmissionPublisher 默认异步分发。这里等待订阅者处理完成，避免 main 过早结束。
        TimeUnit.SECONDS.sleep(2);
    }

    private static class OrderEventSubscriber implements Flow.Subscriber<OrderEvent> {
        private final String name;
        private Flow.Subscription subscription;

        private OrderEventSubscriber(String name) {
            this.name = name;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            log(name + " 完成订阅，先请求 1 条数据");

            // 背压入口：Subscriber 不 request，Publisher 就不会向它推送数据。
            subscription.request(1);
        }

        @Override
        public void onNext(OrderEvent item) {
            log(name + " 收到事件 " + item);
            sleep(250);

            // 处理完一条再请求下一条。
            // 这体现的是响应式流的需求控制：消费者按自己的处理能力请求数据。
            log(name + " 处理完成，请求下一条");
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            log(name + " 处理异常：" + throwable.getMessage());
        }

        @Override
        public void onComplete() {
            log(name + " 收到完成信号 onComplete");
        }
    }

    private record OrderEvent(String orderId, String status) {
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
