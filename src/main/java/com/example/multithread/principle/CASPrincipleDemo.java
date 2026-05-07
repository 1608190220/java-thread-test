package com.example.multithread.principle;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * ============================================================
 * 知识点：CAS（Compare-And-Swap）原理深度解析
 * ============================================================
 *
 * 【什么是 CAS？】
 * ┌─────────────────────────────────────────────────────────────┐
 * │  CAS = Compare-And-Swap（比较并交换）                          │
 * │                                                             │
 * │  它是一种无锁的并发算法，核心思想是：                             │
 * │  "我认为变量 V 的值应该是 A，如果是的话，就把它改成 B，否则不操作"   │
 * │                                                             │
 * │  这整个操作是原子的！由硬件（CPU）直接保证。                       │
 * └─────────────────────────────────────────────────────────────┘
 *
 * 【生活类比：银行转账的"乐观锁"】
 * 想象你要给朋友转账：
 * - 你先查看账户余额：1000元（这是"期望值" A）
 * - 转账时你告诉银行："如果当前余额还是1000元，就扣掉500元变成500元"
 * - 银行会原子性地执行：比较当前余额是否=1000，是则改为500，否则拒绝
 *
 * 这就是乐观锁的思想：先操作，冲突了再重试！
 *
 * 【本 Demo 演示内容】
 * 1. CAS 的生活类比和概念解释
 * 2. 手写模拟 CAS 操作（SimpleCASInteger）
 * 3. 对比：普通 i++ vs CAS 自旋的并发安全性
 * 4. 演示经典的 ABA 问题及解决方案（版本号/时间戳）
 * 5. 解析 AtomicInteger.incrementAndGet() 底层 CAS 原理
 * 6. CAS 的优缺点总结
 *
 */
public class CASPrincipleDemo {

    public static void main(String[] args) throws InterruptedException {
        log("========== CAS 原理演示开始 ==========");

        // ========== 第 1 部分：概念讲解 ==========
        explainCASConcept();

        // ========== 第 2 部分：手写模拟 CAS ==========
        demonstrateHandWrittenCAS();

        // ========== 第 3 部分：对比 i++ vs CAS ==========
        compareNormalIncrementVsCAS();

        // ========== 第 4 部分：ABA 问题演示与解决 ==========
        demonstrateABAProblem();

        // ========== 第 5 部分：AtomicInteger 内部原理 ==========
        analyzeAtomicIntegerInternal();

        // ========== 第 6 部分：优缺点总结 ==========
        summarizeCASProsAndCons();

        log("========== CAS 原理演示结束 ==========");
    }

    /**
     * ============================================================
     * 第 1 部分：CAS 概念详细解释
     * ============================================================
     *
     * CAS 操作包含三个操作数：
     * - V：内存中的变量值（Variable）
     * - A：期望的旧值（Expected / 旧值）
     * - B：要设置的新值（New Value / 新值）
     *
     * 伪代码逻辑：
     * ```
     * boolean CAS(V, A, B) {
     *     if (V == A) {    // 比较：当前值是否等于期望值
     *         V = B;       // 交换：相等则更新为新值
     *         return true; // 成功
     *     }
     *     return false;    // 失败：说明有其他线程修改过
     * }
     * ```
     *
     * 【关键点】这个比较+赋值是 CPU 级别的原子指令（如 x86 的 CMPXCHG），
     * 不需要加锁就能保证线程安全！这就是"无锁编程"的基础。
     */
    private static void explainCASConcept() {
        log("\n--- 第 1 部分：CAS 概念讲解 ---");
        log("");
        log("【CAS 全称】：Compare-And-Swap（比较并交换）");
        log("");
        log("【三个操作数】");
        log("  V (Variable) : 内存中实际的变量值");
        log("  A (Expected)  : 线程预期的旧值");
        log("  B (New Value) : 准备写入的新值");
        log("");
        log("【执行流程】");
        log("  步骤1: 读取内存中 V 的当前值");
        log("  步骤2: 比较 V 是否等于 A（期望值）");
        log("  步骤3a: 如果 V == A，将 V 更新为 B，返回成功");
        log("  步骤3b: 如果 V != A，说明被其他线程改过，返回失败");
        log("");
        log("【为什么是原子的？】");
        log("  因为底层使用的是硬件级别的原子指令：");
        log("  - x86 架构: CMPXCHG 指令");
        log("  - ARM 架构: LDREX/STREX 指令对");
        log("  这些指令在 CPU 层面保证了比较和写入的不可分割性！");
        log("");
        log("【应用场景】");
        log("  Java 的 java.util.concurrent.atomic 包下的所有原子类");
        log("  都是基于 CAS 实现的，如 AtomicInteger、AtomicLong 等。");
    }

    /**
     * ============================================================
     * 第 2 部分：手写模拟 CAS 操作
     * ============================================================
     *
     * 我们用 volatile + while 循环来模拟 CAS 的行为。
     *
     * 【volatile 的作用】
     * - 保证变量的可见性：一个线程修改后，其他线程立即可见
     * - 但不保证原子性：i++ 这种复合操作仍然需要额外处理
     *
     * 【while 循环的作用（自旋/Spin）】
     * - CAS 失败时不断重试，直到成功为止
     * - 这就是所谓的"自旋锁"或"乐观锁"策略
     * - 与"synchronized 悲观锁"不同：悲观锁会阻塞等待，CAS 是忙等待
     */
    private static void demonstrateHandWrittenCAS() throws InterruptedException {
        log("\n--- 第 2 部分：手写模拟 CAS ---");

        // 创建我们手写的 CAS 整数类实例，初始值为 0
        SimpleCASInteger casCounter = new SimpleCASInteger(0);
        int threadCount = 10;
        int incrementsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);

        log("初始值 = " + casCounter.get());
        log("启动 " + threadCount + " 个线程，每个线程执行 " + incrementsPerThread + " 次 ++ 操作");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        // 使用我们手写的 CAS 自增方法
                        casCounter.increment();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        log("最终值 = " + casCounter.get());
        log("期望值 = " + (threadCount * incrementsPerThread));
        log("结果是否正确: " + (casCounter.get() == threadCount * incrementsPerThread));

        if (casCounter.get() != threadCount * incrementsPerThread) {
            log("[ERROR] 结果不一致！这说明 CAS 实现有问题");
        } else {
            log("[OK] 结果完全正确！手写 CAS 实现成功保证了线程安全");
        }
    }

    /**
     * ============================================================
     * 第 3 部分：对比 普通 i++ vs CAS 自旋
     * ============================================================
     *
     * 【普通 i++ 的问题】
     * i++ 看起来是一行代码，但实际上是三步操作：
     *   1. 读取 i 的值到寄存器
     *   2. 在寄存器中 +1
     *   3. 写回内存
     *
     * 这三步不是原子的！多线程环境下会出现"丢失更新"问题。
     *
     * 【CAS 解决方案】
     * 使用 CAS 可以把这三步变成一个原子操作：
     *   "如果当前值是我预期的旧值，就把它+1，否则重试"
     */
    private static void compareNormalIncrementVsCAS() throws InterruptedException {
        log("\n--- 第 3 部分：对比 普通 i++ vs CAS 自旋 ---");

        int threadCount = 10;
        int incrementsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount * 2); // 两组测试

        // 测试1：普通的非线程安全计数器
        UnsafeCounter unsafeCounter = new UnsafeCounter();
        // 测试2：基于 CAS 的安全计数器
        SimpleCASInteger safeCounter = new SimpleCASInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount * 2);

        // 启动操作 unsafe counter 的线程
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        unsafeCounter.unsafeIncrement(); // 非线程安全的 ++
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 启动操作 CAS counter 的线程
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        safeCounter.increment(); // CAS 安全的自增
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        int expected = threadCount * incrementsPerThread;

        log("");
        log("【普通 i++ 计数器】");
        log("  最终值 = " + unsafeCounter.getValue());
        log("  期望值 = " + expected);
        log("  丢失次数 = " + (expected - unsafeCounter.getValue()));
        log("  结论: 非原子操作，多线程下结果偏小（丢失更新）");

        log("");
        log("【CAS 自旋计数器】");
        log("  最终值 = " + safeCounter.get());
        log("  期望值 = " + expected);
        log("  结论: CAS 保证原子性，结果完全正确 ✓");
    }

    /**
     * ============================================================
     * 第 4 部分：ABA 问题演示与解决方案
     * ============================================================
     *
     * 【什么是 ABA 问题？】
     * 场景：
     *   1. 线程1读取值 V = A
     *   2. 线程2把 V 从 A 改成 B
     *   3. 线程2又把 V 从 B 改回 A
     *   4. 线程1做 CAS：发现 V 还是 A，以为没变过，于是成功执行
     *
     * 问题在于：虽然值看起来没变，但实际上中间被修改过了！
     * 在某些场景下这会导致严重问题（如链表节点删除后重新插入）。
     *
     * 【解决方案：带版本号的 CAS】
     * 除了比较值，还比较版本号（或时间戳）：
     * - 初始：(A, version=1)
     * - 线程2改为B: (B, version=2)
     * - 线程2改回A: (A, version=3)
     * - 线程1CAS: 期望(A, v=1)，实际(A, v=3) → 版本不匹配，CAS失败！
     *
     * Java 提供了 AtomicStampedReference 来解决这个问题。
     */
    private static void demonstrateABAProblem() {
        log("\n--- 第 4 部分：ABA 问题演示与解决 ---");

        // ---------- 演示 ABA 问题 ----------
        log("");
        log("【ABA 问题演示】");
        log("场景：一个共享变量，初始值 = 100");

        SimpleCASInteger abaDemo = new SimpleCASInteger(100);
        log("  初始值 = " + abaDemo.get());

        // 模拟主线程读取旧值
        int oldValue = abaDemo.get(); // 主线程读到 100
        log("  主线程读取到旧值 = " + oldValue + ", 准备稍后执行 CAS(100 → 150)");

        // 模拟另一个线程制造 ABA
        log("  [其他线程] 开始制造 ABA...");
        log("    步骤1: 100 → 200 (CAS成功)");
        abaDemo.compareAndSet(100, 200);
        log("    当前值 = " + abaDemo.get());

        log("    步骤2: 200 → 100 (改回原值，制造 ABA)");
        abaDemo.compareAndSet(200, 100);
        log("    当前值 = " + abaDemo.get());

        // 主线程执行 CAS
        log("  [主线程] 执行 CAS: 期望=" + oldValue + ", 新值=150");
        boolean success = abaDemo.compareAndSet(oldValue, 150);
        log("  CAS 结果 = " + success + " (成功了！但中间值被篡改过)");

        if (success) {
            log("  ⚠️ 这就是 ABA 问题：值虽然还是 A，但中间经历过变化！");
        }

        // ---------- 演示解决方案：AtomicStampedReference ----------
        log("");
        log("【解决方案：AtomicStampedReference（带版本号）】");
        log("场景：同样的初始值 = 100，但这次带上版本号");

        AtomicStampedReference<Integer> stampedRef =
                new AtomicStampedReference<>(100, 1); // 初始值100，版本号1

        int initialStamp = stampedRef.getStamp();
        log("  初始值 = " + stampedRef.getReference() + ", 版本号 = " + initialStamp);

        // 主线程读取旧值和旧版本号
        Integer oldValue2 = stampedRef.getReference(); // 100
        int oldStamp = stampedRef.getStamp();          // 1
        log("  主线程读取: 值=" + oldValue2 + ", 版本号=" + oldStamp + ", 准备执行 CAS");

        // 其他线程制造 ABA（但版本号会递增）
        log("  [其他线程] 制造 ABA（注意版本号变化）...");

        // 100→200, 版本号 1→2
        boolean r1 = stampedRef.compareAndSet(oldValue2, 200, oldStamp, oldStamp + 1);
        log("    步骤1: (100,v1) → (200,v2), CAS=" + r1 +
                ", 当前值=" + stampedRef.getReference() + ", 版本=" + stampedRef.getStamp());

        // 200→100, 版本号 2→3
        Integer valueAfterStep1 = stampedRef.getReference();
        int stampAfterStep1 = stampedRef.getStamp();
        boolean r2 = stampedRef.compareAndSet(valueAfterStep1, oldValue2, stampAfterStep1, stampAfterStep1 + 1);
        log("    步骤2: (200,v2) → (100,v3), CAS=" + r2 +
                ", 当前值=" + stampedRef.getReference() + ", 版本=" + stampedRef.getStamp());

        // 主线程尝试 CAS：期望(100, v1)，但实际是(100, v3)
        log("  [主线程] 执行 CAS: 期望(值=" + oldValue2 + ", 版本=" + oldStamp + ") → 新值=150");
        boolean successWithStamp =
                stampedRef.compareAndSet(oldValue2, 150, oldStamp, oldStamp + 1);
        log("  CAS 结果 = " + successWithStamp + " (失败了！因为版本号不匹配)");
        log("  当前值 = " + stampedRef.getReference() + ", 版本号 = " + stampedRef.getStamp());

        if (!successWithStamp) {
            log("  ✓ 成功检测到了 ABA 问题！通过版本号避免了错误更新。");
        }
    }

    /**
     * ============================================================
     * 第 5 部分：AtomicInteger 底层 CAS 原理解析
     * ============================================================
     *
     * AtomicInteger.incrementAndGet() 的源码简化版：
     *
     * ```java
     * public final int incrementAndGet() {
     *     for (;;) {                              // ① 无限循环（自旋）
     *         int current = get();               // ② 读取当前值
     *         int next = current + 1;            // ③ 计算新值
     *         if (compareAndSet(current, next)) { // ④ 尝试 CAS 更新
     *             return next;                   // ⑤ 成功则返回
     *         }
     *         // ⑥ 失败则重试（其他线程抢先修改了值）
     *     }
     * }
     * ```
     *
     * 【关键点】
     * - compareAndSet 调的是 Unsafe 类的 native 方法
     * - 最终映射到 CPU 的 CMPXCHG 原子指令
     * - 自旋次数取决于竞争激烈程度
     */
    private static void analyzeAtomicIntegerInternal() throws InterruptedException {
        log("\n--- 第 5 部分：AtomicInteger 内部原理 ---");

        AtomicInteger atomicInt = new AtomicInteger(0);
        int threadCount = 5;
        int incrementsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);

        log("【incrementAndGet() 执行流程解析】");
        log("");
        log("伪代码：");
        log("  for (;;) {");
        log("      int current = get();              // 读取当前值");
        log("      int next = current + 1;           // 计算新值");
        log("      if (compareAndSet(current, next)) // CAS原子更新");
        log("          return next;                  // 成功返回");
        log("      // 失败则自旋重试...");
        log("  }");
        log("");

        log("实际运行测试：初始值=" + atomicInt.get());
        log("启动 " + threadCount + " 个线程，各自调用 incrementAndGet() " + incrementsPerThread + " 次");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        atomicInt.incrementAndGet(); // 调用 AtomicInteger 的 CAS 自增
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        log("最终值 = " + atomicInt.get());
        log("期望值 = " + (threadCount * incrementsPerThread));
        log("耗时 = " + elapsedMs + "ms");
        log("");

        log("【底层调用链】");
        log("  AtomicInteger.incrementAndGet()");
        log("    → Unsafe.getAndAddInt()           // JNI native 方法");
        log("      → atomic::cmpxchg (x86)         // CPU 原子指令");
        log("        或 LDREX/STREX (ARM)          // ARM 原子指令对");
    }

    /**
     * ============================================================
     * 第 6 部分：CAS 优缺点总结
     * ============================================================
     */
    private static void summarizeCASProsAndCons() {
        log("\n--- 第 6 部分：CAS 优缺点总结 ---");
        log("");
        log("【优点】");
        log("  1. 无锁（Lock-Free）：不需要使用 synchronized 或 ReentrantLock");
        log("     → 不会发生线程上下文切换（从用户态到内核态），性能更高");
        log("  2. 非阻塞：线程不会挂起进入等待队列，而是自旋重试");
        log("     → 适合竞争不激烈、持有锁时间短的场景");
        log("  3. 原子性由硬件保证：利用 CPU 的原子指令，可靠性高");
        log("  4. 是 Java 并发包（JUC）的基石：AtomicXXX、ConcurrentHashMap 等");
        log("");
        log("【缺点 / 局限性】");
        log("  1. ABA 问题：值从 A→B→A，CAS 无法感知中间变化");
        log("     → 解决方案：AtomicStampedReference（加版本号）");
        log("  2. 自旋 CPU 消耗：高并发时大量线程空转（busy-wait）");
        log("     → 如果长时间 CAS 失败，会浪费 CPU 资源");
        log("     → LongAdder 采用分段 CAS 来缓解这个问题");
        log("  3. 只能保证单个变量的原子性：");
        log("     → 无法像 synchronized 那样对多个操作组成临界区");
        log("     → 多个共享变量需要配合其他机制");
        log("  4. 公平性问题：CAS 是非公平的，先来的线程不一定先成功");
        log("");
        log("【适用场景】");
        log("  ✓ 读多写少、竞争不激烈的场景");
        log("  ✓ 单个原子变量的更新（如计数器、状态标志）");
        log("  ✓ 需要高性能、低延迟的无锁数据结构");
        log("【适用业务场景】");
        log("  ✓ 计数器：高并发下统计请求次数、在线人数、点赞数");
        log("  ✓ ID生成器：生成全局唯一的递增序列号");
        log("  ✓ 状态标志：布尔型开关（AtomicBoolean），确保状态只改变一次。");
        log("");
        log("【不适用场景】");
        log("  ✗ 竞争非常激烈（大量线程同时修改同一变量）");
        log("  ✗ 需要多个操作的原子组合（如：检查再操作 check-then-act）");
        log("  ✗ 需要公平性保证的场景");
        log("【不适用业务场景】");
        log("  ✗ 栈顶元素被弹出、再推入另一个“看起来一样”的对象，栈结构已变。");
        log("  ✗ 超过200个线程疯狂更新同一个AtomicInteger。");
        log("  ✗ 银行转账更新A的余额减100、B的余额加100。");
    }

    /**
     * ============================================================
     * 手写的简单 CAS 整数类（用于教学演示）
     * ============================================================
     *
     * 这个类模拟了 AtomicInteger 的核心思想：
     * - 使用 volatile 保证可见性
     * - 使用 while 循环实现自旋（CAS 失败时重试）
     *
     * 注意：这只是教学用的简化版，真正的 AtomicInteger
     * 使用的是 sun.misc.Unsafe 类调用 CPU 原子指令。
     */
    static class SimpleCASInteger {

        /**
         * volatile 关键字的作用：
         * 1. 可见性：当一个线程修改了 value，其他线程能立即看到最新值
         * 2. 有序性：禁止指令重排序（happens-before 原则）
         *
         * 但 volatile 不保证原子性！所以还需要 CAS 机制配合。
         */
        private volatile int value;

        public SimpleCASInteger(int initialValue) {
            this.value = initialValue;
        }

        /**
         * 获取当前值
         */
        public int get() {
            return value;
        }

        /**
         * 核心方法：CAS（Compare-And-Swap）
         *
         * @param expect 期望的旧值（我认为它应该是什么）
         * @param update 要设置的新值（我想把它改成什么）
         * @return 如果当前值 == expect，则更新为 update 并返回 true；否则返回 false
         *
         * 【执行过程详解】
         * 1. 读取当前的 value 到局部变量 current
         * 2. 比较 current 是否等于 expect
         * 3. 如果相等，把 value 设为 update，返回 true（CAS 成功）
         * 4. 如果不等，说明其他线程修改了 value，返回 false（CAS 失败）
         *
         * 注意：这里使用了 synchronized 来保证 compareAndSet 本身的原子性，
         * 但在实际的 AtomicInteger 中，这一步是由硬件原子指令完成的，
         * 不需要任何锁！这里只是为了在 Java 层面模拟其语义。
         */
        public synchronized boolean compareAndSet(int expect, int update) {
            if (value == expect) {
                value = update;
                return true; // CAS 成功：值没有被其他线程改过
            }
            return false;    // CAS 失败：值已经被其他线程修改
        }

        /**
         * 基于 CAS 的线程安全自增操作
         *
         * 【自旋（Spin）机制】
         * 这是一个典型的 CAS 自旋模式：
         * 1. 先读取当前值 current
         * 2. 计算新值 next = current + 1
         * 3. 尝试 CAS：如果 value 还是 current，就改为 next
         * 4. 如果 CAS 失败（其他线程抢先修改了），回到步骤 1 重试
         * 5. 直到 CAS 成功为止
         *
         * 为什么用 while 而不是 if？
         * 因为 CAS 可能会多次失败（高并发场景下），
         * 必须一直重试直到成功，才能保证正确性。
         */
        public void increment() {
            while (true) {
                int current = get();       // 步骤1: 读取当前值
                int next = current + 1;    // 步骤2: 计算新值

                // 步骤3: 尝试 CAS 更新
                // 只有当 value 没有被其他线程改变时（还是 current），才能成功更新
                if (compareAndSet(current, next)) {
                    return; // CAS 成功，退出循环
                }
                // CAS 失败：说明有其他线程抢先修改了 value
                // 重新读取最新的 value，再次尝试（自旋/重试）
            }
        }
    }

    /**
     * 非线程安全的计数器（用于对比演示）
     */
    static class UnsafeCounter {
        private int value = 0;

        /**
         * 非线程安全的 i++ 操作
         *
         * 【为什么不安全？】
         * i++ 实际上分为三步（非原子操作）：
         *   1. LOAD: 读取 value 到寄存器/工作内存
         *   2. ADD:  在寄存器中 +1
         *   3. STORE: 把结果写回主内存
         *
         * 多线程环境下可能这样交错执行：
         *   线程A: LOAD(value=100)
         *   线程B: LOAD(value=100)  ← 读到相同的旧值
         *   线程A: ADD  → 101
         *   线程B: ADD  → 101      ← 两个线程都得到101，丢失了一次++
         *   线程A: STORE(value=101)
         *   线程B: STORE(value=101) ← 最终只增加了1，而不是2
         */
        public void unsafeIncrement() {
            value++; // 这一行不是原子操作！
        }

        public int getValue() {
            return value;
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [CAS-Demo] %s%n", LocalTime.now(), msg);
    }
}
