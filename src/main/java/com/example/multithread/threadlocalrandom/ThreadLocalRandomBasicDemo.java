package com.example.multithread.threadlocalrandom;

import java.time.LocalTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 知识点：ThreadLocalRandom 独立专题。
 *
 * ThreadLocalRandom 是专门为“多线程环境下生成随机数”设计的工具类。
 *
 * 核心理解：
 * 1. Random 可以被多个线程共享，但共享同一个 Random 时，内部随机种子更新会形成竞争。
 * 2. ThreadLocalRandom 把随机种子拆到每个线程自己的状态里，线程之间不共享同一个种子更新点。
 * 3. 使用方式不是 new ThreadLocalRandom()，而是 ThreadLocalRandom.current()。
 * 4. ThreadLocalRandom 不允许业务代码手动 setSeed，因为它的种子由 JDK 在线程内部维护。
 *
 * 适用场景：
 * - 多线程里生成订单尾号、测试数据、随机退避时间、随机抽样。
 * - 不要求密码学安全的随机数。
 *
 * 不适用场景：
 * - token、验证码、密钥、盐值等安全敏感场景，应使用 java.security.SecureRandom。
 */
public class ThreadLocalRandomBasicDemo {

    public static void main(String[] args) {
        log("===== ThreadLocalRandom 基础用法 =====");

        generateBusinessValues();
        generateRandomBackoff();
        demonstrateNoManualSeed();
    }

    /**
     * 演示常见的业务随机值生成。
     *
     * 注意：
     * - nextInt(origin, bound) 是左闭右开区间：[origin, bound)。
     * - nextLong(origin, bound)、nextDouble(origin, bound) 也是同样语义。
     */
    private static void generateBusinessValues() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int orderSuffix = random.nextInt(100_000, 1_000_000);
        long couponId = random.nextLong(10_000_000_000L, 99_999_999_999L);
        double discount = random.nextDouble(0.80, 0.96);
        boolean hitGrayRelease = random.nextInt(100) < 20;

        log("订单随机尾号：" + orderSuffix);
        log("优惠券随机编号：" + couponId);
        log("折扣随机系数：" + String.format("%.2f", discount));
        log("是否命中 20% 灰度流量：" + hitGrayRelease);
    }

    /**
     * 演示“随机退避”。
     *
     * 在重试、限流、抢锁失败后，很多系统会让线程等待一个随机时间再继续，
     * 避免大量线程在同一个时间点再次同时冲击下游服务或同一把锁。
     */
    private static void generateRandomBackoff() {
        log("\n===== 随机退避时间 =====");

        for (int retry = 1; retry <= 5; retry++) {
            int backoffMs = ThreadLocalRandom.current().nextInt(100, 501);
            log("第 " + retry + " 次重试前随机等待：" + backoffMs + "ms");
        }
    }

    /**
     * ThreadLocalRandom 不允许手动设置种子。
     *
     * 这是一个设计选择：
     * - Random 面向“一个对象维护一份随机状态”；
     * - ThreadLocalRandom 面向“每个线程维护自己的随机状态”；
     * - 如果允许外部随意 setSeed，就会破坏它在线程内部管理种子的模型。
     */
    private static void demonstrateNoManualSeed() {
        log("\n===== ThreadLocalRandom 不支持 setSeed =====");

        try {
            ThreadLocalRandom.current().setSeed(123L);
        } catch (UnsupportedOperationException e) {
            log("调用 setSeed(123L) 失败：" + e.getClass().getSimpleName());
        }
    }

    private static void log(String message) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), message);
    }
}
