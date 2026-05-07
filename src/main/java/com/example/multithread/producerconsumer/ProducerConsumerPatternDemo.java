package com.example.multithread.producerconsumer;

import java.time.LocalTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 知识点：生产者-消费者模式（Producer-Consumer Pattern）。
 *
 * <p>生产者-消费者模式用于解耦“产生数据的线程”和“处理数据的线程”：
 * 1. 生产者只负责把任务放入共享缓冲区，不直接调用消费者。
 * 2. 消费者只负责从共享缓冲区取任务处理，不关心任务由谁生产。
 * 3. 共享缓冲区通常使用有界队列，容量有限可以形成背压，避免生产速度过快导致内存无限增长。
 *
 * <p>本示例使用 BlockingQueue 作为共享缓冲区：
 * - queue.put(...)：队列满时自动阻塞生产者，直到消费者取走元素。
 * - queue.take()：队列空时自动阻塞消费者，直到生产者放入元素。
 * - 毒丸消息：生产完成后放入特殊任务，消费者收到后退出循环，实现优雅停机。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.producerconsumer.ProducerConsumerPatternDemo
 */
public class ProducerConsumerPatternDemo {

    /**
     * 消费者线程数量。
     *
     * <p>每一个消费者都需要收到一个“停止信号”才能退出，因此后面会放入 CONSUMER_COUNT 个毒丸消息。
     */
    private static final int CONSUMER_COUNT = 2;

    /**
     * 队列容量故意设置得比较小，方便在控制台观察：
     * 当生产速度快于消费速度时，队列会被放满，生产者会在 put 方法上阻塞等待。
     */
    private static final int QUEUE_CAPACITY = 3;

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Product> warehouse = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        log("示例开始：2 个生产者、2 个消费者，共用容量为 " + QUEUE_CAPACITY + " 的仓库队列");

        // 两个生产者分别生产不同范围的商品编号，模拟多个上游线程同时写入队列。
        Thread producerA = new Thread(new Producer("生产者-A", 1, 5, warehouse), "producer-a");
        Thread producerB = new Thread(new Producer("生产者-B", 6, 10, warehouse), "producer-b");

        // 两个消费者并行从同一个队列取数据，模拟多个下游线程共同处理任务。
        Thread consumer1 = new Thread(new Consumer("消费者-1", warehouse), "consumer-1");
        Thread consumer2 = new Thread(new Consumer("消费者-2", warehouse), "consumer-2");

        producerA.start();
        producerB.start();
        consumer1.start();
        consumer2.start();

        /*
         * 主线程等待两个生产者全部结束后，再统一发送停止信号。
         *
         * 这样可以避免一个生产者先结束就投递毒丸，导致消费者提前退出，
         * 而另一个生产者后续生产的普通商品无人消费。
         */
        producerA.join();
        producerB.join();

        for (int i = 1; i <= CONSUMER_COUNT; i++) {
            warehouse.put(Product.poisonPill());
            log("主线程投递第 " + i + " 个停止信号");
        }

        consumer1.join();
        consumer2.join();

        log("示例结束：所有生产者和消费者均已退出");
    }

    /**
     * 生产者：负责创建商品并放入队列。
     *
     * <p>生产者不需要知道有几个消费者，也不需要直接调用消费者方法。
     * 它只和 BlockingQueue 打交道，这就是生产者与消费者解耦的关键。
     */
    private static class Producer implements Runnable {
        private final String producerName;
        private final int startNo;
        private final int endNo;
        private final BlockingQueue<Product> warehouse;

        Producer(String producerName, int startNo, int endNo, BlockingQueue<Product> warehouse) {
            this.producerName = producerName;
            this.startNo = startNo;
            this.endNo = endNo;
            this.warehouse = warehouse;
        }

        @Override
        public void run() {
            try {
                for (int no = startNo; no <= endNo; no++) {
                    Product product = Product.normal(no, producerName);

                    log(producerName + " 准备入库 " + product.name()
                            + "，当前库存=" + warehouse.size());

                    /*
                     * put 是阻塞方法：
                     * - 如果队列没满，商品立即入队；
                     * - 如果队列已满，当前生产者线程会在这里等待；
                     * - 等消费者 take 走元素后，生产者才会继续执行。
                     */
                    warehouse.put(product);

                    log(producerName + " 完成入库 " + product.name()
                            + "，当前库存=" + warehouse.size());

                    // 模拟生产耗时。这里生产比消费略快，更容易看到队列堆积和背压。
                    TimeUnit.MILLISECONDS.sleep(120);
                }

                log(producerName + " 已完成全部生产任务");
            } catch (InterruptedException e) {
                /*
                 * 捕获 InterruptedException 后恢复中断标记，是并发代码中的常见规范。
                 * 这样上层代码仍然可以通过 Thread.currentThread().isInterrupted() 感知中断。
                 */
                Thread.currentThread().interrupt();
                log(producerName + " 被中断，提前停止生产");
            }
        }
    }

    /**
     * 消费者：负责从队列取商品并处理。
     *
     * <p>消费者不关心商品来自哪个生产者，也不关心生产速度。
     * 当队列为空时，take 会让消费者线程等待，而不是空转浪费 CPU。
     */
    private static class Consumer implements Runnable {
        private final String consumerName;
        private final BlockingQueue<Product> warehouse;

        Consumer(String consumerName, BlockingQueue<Product> warehouse) {
            this.consumerName = consumerName;
            this.warehouse = warehouse;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    /*
                     * take 是阻塞方法：
                     * - 如果队列有数据，立即取出队头元素；
                     * - 如果队列为空，当前消费者线程会在这里等待；
                     * - 等生产者 put 新元素后，消费者才会继续执行。
                     */
                    Product product = warehouse.take();

                    if (product.isPoisonPill()) {
                        log(consumerName + " 收到停止信号，准备退出");
                        break;
                    }

                    log(consumerName + " 取出 " + product.name()
                            + "，来源=" + product.source()
                            + "，剩余库存=" + warehouse.size());

                    // 模拟消费耗时。消费较慢时，能观察到生产者被队列容量限制住。
                    TimeUnit.MILLISECONDS.sleep(350);

                    log(consumerName + " 处理完成 " + product.name());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log(consumerName + " 被中断，提前停止消费");
            }
        }
    }

    /**
     * 队列中传递的数据对象。
     *
     * <p>普通商品用于业务处理；毒丸商品只用于通知消费者退出，不参与业务处理。
     */
    private static class Product {
        private final int no;
        private final String name;
        private final String source;
        private final boolean poisonPill;

        private Product(int no, String name, String source, boolean poisonPill) {
            this.no = no;
            this.name = name;
            this.source = source;
            this.poisonPill = poisonPill;
        }

        static Product normal(int no, String source) {
            return new Product(no, "商品-" + no, source, false);
        }

        static Product poisonPill() {
            return new Product(-1, "停止信号", "system", true);
        }

        String name() {
            return name;
        }

        String source() {
            return source;
        }

        boolean isPoisonPill() {
            return poisonPill;
        }
    }

    private static void log(String message) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), message);
    }
}
