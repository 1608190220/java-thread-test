package com.example.multithread.principle;

import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Lock;

/**
 * ============================================================
 * 知识点：AQS（AbstractQueuedSynchronizer）原理深度解析
 * ============================================================
 *
 * 【什么是 AQS？】
 * ┌─────────────────────────────────────────────────────────────┐
 * │  AQS = AbstractQueuedSynchronizer（抽象队列同步器）            │
 * │                                                             │
 * │  它是 Java 并发包（JUC）中几乎所有锁和同步器的基石！               │
 * │  ReentrantLock、Semaphore、CountDownLatch、                  │
 * │  ReentrantReadWriteLock、CyclicBarrier 等                    │
 * │  全部都基于 AQS 或其子类实现。                                  │
 * │                                                             │
 * │  AQS 的核心思想是用极少的代码实现多种同步器。                      │
 * └─────────────────────────────────────────────────────────────┘
 *
 * 【AQS 的两大核心组件】
 *
 *   ┌─────────────────────────────────────────────────────┐
 *   │                    AQS 架构                          │
 *   ├─────────────────────────────────────────────────────┤
 *   │                                                     │
 *   │   ┌──────────┐         ┌───────────────────────┐    │
 *   │   │  state    │         │     CLH 同步队列      │    │
 *   │   │  变量     │         │  (双向链表)            │    │
 *   │   │          │         │                       │    │
 *   │   │ • int 类型│         │  HEAD → Node → Node   │    │
 *   │   │ • volatile│         │              ↓       │    │
 *   │   │ • 表示    │         │            TAIL       │    │
 *   │   │   同步状态│         │                       │    │
 *   │   └──────────┘         └───────────────────────┘    │
 *   │                                                     │
 *   │   含义由子类定义：          存储等待获取同步状态的线程     │
 *   │   • ReentrantLock:        每个节点包含：              │
 *   │     state=0 未锁定         • thread (等待的线程)       │
 *   │     state>0 重入次数       • waitStatus (等待状态)     │
 *   │   • Semaphore:            • prev/next (前后指针)     │
 *   │     state=剩余许可证数                                │
 *   │   • CountDownLatch:                                 │
 *   │     state=剩余计数                                   │
 *   └─────────────────────────────────────────────────────┘
 *
 * 【本 Demo 演示内容】
 * 1. AQS 核心概念与架构图解
 * 2. 手写基于 AQS 的非可重入互斥锁（独占模式）
 * 3. 手写基于 AQS 的共享式计数信号量（共享模式）
 * 4. 对比 ReentrantLock/Semaphore/CountDownLatch 如何复用 AQS
 * 5. CLH 队列的等待/唤醒机制解析
 *
 */
public class AQSPrincipleDemo {

    public static void main(String[] args) throws InterruptedException {
        log("========== AQS 原理演示开始 ==========");

        // ========== 第 1 部分：AQS 核心概念讲解 ==========
        explainAQSCoreConcepts();

        // ========== 第 2 部分：手写互斥锁（独占模式） ==========
        demonstrateExclusiveLock();

        // ========== 第 3 部分：手写信号量（共享模式） ==========
        demonstrateSharedSemaphore();

        // ========== 第 4 部分：对比 JUC 组件如何使用 AQS ==========
        compareJUCComponents();

        // ========== 第 5 部分：CLH 队列机制详解 ==========
        explainCLHQueueMechanism();

        log("========== AQS 原理演示结束 ==========");
    }

    /**
     * ============================================================
     * 第 1 部分：AQS 核心概念详细讲解
     * ============================================================
     *
     * 【设计模式：模板方法模式】
     * AQS 使用了经典的模板方法模式：
     * - AQS 本身实现了"同步队列管理"的通用逻辑（模板方法）
     * - 子类只需要实现"状态判断/修改"的具体逻辑（钩子方法）
     *
     * 【子类必须实现的方法】
     *
     *   独占模式（Exclusive）：                共享模式（Shared）：
     *   ┌──────────────────────┐              ┌──────────────────────┐
     *   │ tryAcquire(int)      │              │ tryAcquireShared(int)│
     *   │   尝试获取锁（独占）  │              │   尝试获取共享资源    │
     *   ├──────────────────────┤              ├──────────────────────┤
     *   │ tryRelease(int)      │              │ tryReleaseShared(int)│
     *   │   尝试释放锁（独占）  │              │   尝试释放共享资源    │
     *   └──────────────────────┘              └──────────────────────┘
     *
     *   可选实现：
     *   - isHeldExclusively(): 当前线程是否独占锁（用于条件变量）
     *
     * 【AQS 提供的模板方法（直接调用即可）】
     * - acquire() / acquireInterruptibly()     : 获取锁（独占）
     * - release()                              : 释放锁（独占）
     * - acquireShared() / acquireSharedInterruptibly(): 获取共享资源
     * - releaseShared()                        : 释放共享资源
     */
    private static void explainAQSCoreConcepts() {
        log("\n--- 第 1 部分：AQS 核心概念 ---");
        log("");
        log("【AQS 全称】：AbstractQueuedSynchronizer（抽象队列同步器）");
        log("【所在包】：java.util.concurrent.locks");
        log("");
        log("【核心设计思想】：模板方法模式");
        log("");
        log("  AQS 封装了通用的同步队列管理逻辑（入队、出队、阻塞、唤醒），");
        log("  子类只需根据业务需求实现状态获取/释放的具体判断逻辑。");
        log("");
        log("【两大核心数据结构】");
        log("");
        log("  1. state 变量（volatile int）:");
        log("     - 用途：表示同步器的状态");
        log("     - 不同子类有不同含义：");
        log("       * ReentrantLock: 0=未锁定, N=重入N次");
        log("       * Semaphore:    剩余许可证数量");
        log("       * CountDownLatch: 剩余计数值");
        log("");
        log("  2. CLH 双向队列（变种）:");
        log("     - 用途：存储等待获取同步状态的线程");
        log("     - 结构：HEAD(虚节点) ↔ Node1 ↔ Node2 ↔ ... ↔ TAIL");
        log("     - 每个 Node 包含: thread, waitStatus, prev, next");
        log("");
        log("【两种同步模式】");
        log("");
        log("  独占模式（Exclusive）:");
        log("    - 同一时刻只能有一个线程持有同步状态");
        log("    - 典型实现: ReentrantLock");
        log("    - 需要实现: tryAcquire(), tryRelease()");
        log("");
        log("  共享模式（Shared）:");
        log("    - 同一时刻可以有多个线程持有同步状态");
        log("    - 典型实现: Semaphore, CountDownLatch");
        log("    - 需要实现: tryAcquireShared(), tryReleaseShared()");
        log("");
        log("【Node 节点的 waitStatus 取值】");
        log("  1. CANCELLED (=1):  节点已取消（超时或中断）");
        log("  2. SIGNAL    (=-1): 后继节点需要被唤醒");
        log("  3. CONDITION (=-2): 节点在条件队列中等待");
        log("  4. PROPAGATE  (=-3): 共享模式下传播唤醒动作");
        log("  0:                     初始状态");
    }

    /**
     * ============================================================
     * 第 2 部分：手写基于 AQS 的非可重入互斥锁
     * ============================================================
     *
     * 我们要实现一个简单的互斥锁，功能类似 ReentrantLock（但不支持重入）。
     *
     * 【实现步骤】
     * 1. 内部类继承 AbstractQueuedSynchronizer
     * 2. 实现 tryAcquire(): 尝试将 state 从 0 改为 1
     * 3. 实现 tryRelease(): 将 state 从 1 改为 0
     * 4. 外部类实现 Lock 接口，委托给 AQS
     *
     * 【tryAcquire 执行流程】
     * ```
     * 调用 lock() → AQS.acquire() → tryAcquire(1)
     *                                    ↓
     *                          if (compareAndSetState(0, 1))
     *                              成功！当前线程获得锁
     *                          else
     *                              失败！AQS 把当前线程加入等待队列并阻塞
     * ```
     *
     * 【tryRelease 执行流程】
     * ```
     * 调用 unlock() → AQS.release() → tryRelease(1)
     *                                      ↓
     *                            setState(0)
     *                            AQS 唤醒等待队列中的后继节点
     * ```
     */
    private static void demonstrateExclusiveLock() throws InterruptedException {
        log("\n--- 第 2 部分：手写互斥锁（独占模式）---");

        // 创建我们手写的互斥锁
        NonReentrantMutex mutex = new NonReentrantMutex();
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);

        log("创建了一个 NonReentrantMutex（基于 AQS 的互斥锁）");
        log("启动 " + threadCount + " 个线程竞争这把锁");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        long startTime = System.nanoTime();

        for (int i = 1; i <= threadCount; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    mutex.lock(); // 获取锁（如果被占用则进入 AQS 等待队列）

                    log("任务-" + taskId + " 获得了锁，正在执行临界区代码...");
                    Thread.sleep(2000); // 模拟耗时操作

                    log("任务-" + taskId + " 即将释放锁");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log("任务-" + taskId + " 被中断");
                } finally {
                    mutex.unlock(); // 释放锁，AQS 会唤醒等待队列中的下一个线程
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        log("所有任务执行完毕，总耗时约 " + elapsedMs + "ms");
        log("（由于是互斥锁，各任务是串行执行的，总耗时 ≈ 任务数 × 单个任务耗时）");
    }

    /**
     * ============================================================
     * 第 3 部分：手写基于 AQS 的共享式计数信号量
     * ============================================================
     *
     * 实现一个简化的 Semaphore，支持多个线程同时访问（受许可证数限制）。
     *
     * 【独占 vs 共享的区别】
     * - 独占（Exclusive）：state=0/1，同一时刻只有一个线程能持有
     * - 共享（Shared）：state=N，同一时刻最多 N 个线程可以持有
     *
     * 【tryAcquireShared 的返回值含义】
     * - 负数：获取失败，需要进入等待队列
     * - 零：获取成功，但没有剩余资源了
     * - 正数：获取成功，还有剩余资源，可以继续唤醒后续共享节点
     */
    private static void demonstrateSharedSemaphore() throws InterruptedException {
        log("\n--- 第 3 部分：手写信号量（共享模式）---");

        // 创建我们手写的信号量，允许同时 2 个线程通过
        SimpleSemaphore semaphore = new SimpleSemaphore(2);
        int totalThreads = 6;
        CountDownLatch latch = new CountDownLatch(totalThreads);

        log("创建了一个 SimpleSemaphore（许可证数 = 2）");
        log("启动 " + totalThreads + " 个线程竞争这 2 个许可证");

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);

        for (int i = 1; i <= totalThreads; i++) {
            final int carId = i;
            executor.submit(() -> {
                try {
                    semaphore.acquire(); // 获取许可证（共享模式，可多个线程同时持有）

                    log("车辆-" + carId + " 获得许可，进入停车场（当前可用=" + semaphore.availablePermits() + "）");
                    Thread.sleep(300); // 模拟停车时间

                    log("车辆-" + carId + " 离开停车场");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log("车辆-" + carId + " 被中断");
                } finally {
                    semaphore.release(); // 释放许可证
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        log("所有车辆处理完毕");
    }

    /**
     * ============================================================
     * 第 4 部分：对比 JUC 组件如何复用 AQS
     * ============================================================
     *
     * 展示不同的 JUC 同步器如何通过实现不同的 tryAcquire/tryRelease
     * 来复用 AQS 的排队/阻塞/唤醒机制。
     */
    private static void compareJUCComponents() {
        log("\n--- 第 4 部分：JUC 组件对 AQS 的复用方式 ---");
        log("");
        log("【不同同步器对 state 的不同诠释】");
        log("");
        log("┌─────────────────┬─────────────────────┬──────────────────────────┐");
        log("│     同步器       │    state 含义        │      特殊行为            │");
        log("├─────────────────┼─────────────────────┼──────────────────────────┤");
        log("│ ReentrantLock   │ 0=空闲, N=重入次数   │ 可重入：同线程可多次acquire│");
        log("│                 │                     │ 公平/非公平：是否插队     │");
        log("├─────────────────┼─────────────────────┼──────────────────────────┤");
        log("│ Semaphore       │ 剩余许可证数量       │ 共享模式：多线程可同时持有│");
        log("│                 │                     │ acquire(N)/release(N)     │");
        log("├─────────────────┼─────────────────────┼──────────────────────────┤");
        log("│ CountDownLatch  │ 倒计时剩余值         │ 只能减不能增！            │");
        log("│                 │                     │ 归零后唤醒所有等待线程     │");
        log("├─────────────────┼─────────────────────┼──────────────────────────┤");
        log("│ ReentrantRWLock │ 低16位=写锁重入次   │ 独占(写)+共享(读)混合    │");
        log("│                 │ 高16位=读锁持有数   │ 写锁排斥所有，读锁兼容读   │");
        log("└─────────────────┴─────────────────────┴──────────────────────────┘");
        log("");
        log("【关键洞察】");
        log("  AQS 的精妙之处在于：它把'同步状态的等待队列管理'这个通用问题");
        log("  解决得非常彻底，而把'什么条件下可以获取/释放状态'这个问题留给子类。");
        log("  这就是'框架'的核心价值：分离变化点和稳定点！");
    }

    /**
     * ============================================================
     * 第 5 部分：CLH 队列等待/唤醒机制详解
     * ============================================================
     *
     * 【CLH 队列的基本结构】
     *
     *   初始状态:
     *   ┌───────┐
     *   │ HEAD  │ ←→ null (虚拟头节点)
     *   │ TAIL  │
     *   └───────┘
     *
     *   线程1获取失败后入队:
     *   ┌───────┐     ┌───────┐
     *   │ HEAD  │ ←→ │ Node1 │ (thread=线程1, waitStatus=0)
     *   │       │     │ TAIL  │
     *   └───────┘     └───────┘
     *
     *   线程2也获取失败，追加到队尾:
     *   ┌───────┐     ┌───────┐     ┌───────┐
     *   │ HEAD  │ ←→ │ Node1 │ ←→ │ Node2 │
     *   │       │     │       │     │ TAIL  │
     *   └───────┘     └───────┘     └───────┘
     *
     * 【节点的等待/唤醒流程】
     *
     *   1. 新节点入队时：
     *      - 将新节点的 prev 指向当前 tail
     *      - CAS 更新 tail 为新节点
     *      - 原尾节点的 next 指向新节点
     *
     *   2. 节点获取失败后的等待：
     *      - 将前驱节点的 waitStatus 设为 SIGNAL（表示后继需要唤醒）
     *      - 调用 LockSupport.park(this) 挂起当前线程
     *
     *   3. 锁释放时的唤醒：
     *      - 头节点的后继节点被 unpark
     *      - 被唤醒的节点尝试再次获取锁
     *      - 如果成功，将自己设为新的头节点
     */
    private static void explainCLHQueueMechanism() {
        log("\n--- 第 5 部分：CLH 队列机制详解 ---");
        log("");
        log("【CLH 队列名称来源】");
        log("  CLH = Craig, Landin, and Hagersten 三位发明者的名字缩写");
        log("  AQS 使用的是 CLH 队列的变种（原始 CLH 是自旋锁，AQS 改为阻塞锁）");
        log("");
        log("【队列基本结构（双向链表）】");
        log("");
        log("  HEAD(虚拟节点) ↔ Node(Thread-A) ↔ Node(Thread-B) ↔ TAIL");
        log("                      ↑                  ↑");
        log("                  第二个等待者          最后一个等待者");
        log("");
        log("【获取锁失败的入队流程】");
        log("");
        log("  1. 线程调用 tryAcquire() 失败");
        log("  2. 构建一个新的 Node(node), 包含当前线程引用");
        log("  3. 通过 CAS 操作将 node 追加到队列尾部");
        log("  4. 设置前驱节点的 waitStatus = SIGNAL");
        log("     （告诉前驱节点：你释放锁的时候记得唤醒我）");
        log("  5. 调用 LockSupport.park(this) 挂起当前线程");
        log("     （线程进入 WAITING 状态，不消耗 CPU）");
        log("");
        log("【释放锁时的唤醒流程】");
        log("");
        log("  1. 线程调用 tryRelease() 成功，state 变为 0");
        log("  2. AQS 检查头节点的后继节点");
        log("  3. 如果后继节点的 waitStatus == SIGNAL:");
        log("     a) 调用 LockSupport.unpark(后继节点.thread)");
        log("     b) 后继节点线程从 park 返回，重新尝试 tryAcquire()");
        log("  4. 如果 tryAcquire() 成功:");
        log("     a) 该节点成为新的 HEAD（原 head 出队）");
        log("     b) 原节点的 thread 引用置空（帮助 GC）");
        log("");
        log("【为什么用双向链表？】");
        log("  - next 指针：用于从前往后传播唤醒信号");
        log("  - prev 指针：用于取消节点时将其从队列中断开");
        log("");
        log("【公平性保证】");
        log("  AQS 严格按 FIFO 顺序唤醒等待线程（先进先出）");
        log("  但注意：非公平锁允许新来的线程'插队'（在入队前先 CAS 抢一次）");
        log("  ReentrantLock 默认是非公平的，可以通过构造参数选择公平模式");
    }

    /**
     * ============================================================
     * 手写实现：基于 AQS 的非可重入互斥锁（独占模式示例）
     * ============================================================
     *
     * 这个类展示了如何利用 AQS 实现一个简单的互斥锁。
     *
     * 【关键实现要点】
     * 1. 内部类 Sync 继承 AbstractQueuedSynchronizer
     * 2. tryAcquire: 用 CAS 将 state 从 0 改为 1
     * 3. tryRelease: 将 state 设为 0
     * 4. 外部委托调用 AQS 的 acquire/release
     */
    static class NonReentrantMutex implements Lock {

        /** 内部同步器，继承 AQS */
        private final Sync sync = new Sync();

        /**
         * 同步器内部类：继承 AbstractQueuedSynchronizer
         *
         * AQS 要求子类必须实现以下方法之一（取决于使用的模式）：
         * - 独占模式: tryAcquire(), tryRelease()
         */
        private static class Sync extends AbstractQueuedSynchronizer {

            /**
             * 尝试以独占模式获取同步状态
             *
             * 【执行逻辑】
             * 1. 检查 state 是否为 0（未被占用）
             * 2. 如果为 0，尝试用 CAS 将其改为 1
             * 3. CAS 成功则返回 true（获取锁成功）
             * 4. CAS 失败或 state != 0 则返回 false（获取锁失败）
             *
             * 注意：这里不支持重入（与 ReentrantLock 的区别）
             * ReentrantLock 会检查当前线程是否已持有锁
             *
             * @param arg 获取的资源数量（通常为 1）
             * @return 是否获取成功
             */
            @Override
            protected boolean tryAcquire(int arg) {
                // 【原子操作】CAS 比较 state 是否为 0
                // 如果是 0（未锁定），则原子地设置为 1（已锁定）
                if (compareAndSetState(0, 1)) {
                    // CAS 成功：设置独占线程为当前线程
                    setExclusiveOwnerThread(Thread.currentThread());
                    return true; // 获取锁成功！
                }
                // CAS 失败：说明 state 已经是 1（已被其他线程锁定）
                return false; // 获取锁失败，AQS 会将当前线程放入等待队列
            }

            /**
             * 尝试释放独占模式的同步状态
             *
             * 【执行逻辑】
             * 1. 检查当前线程是否是持有锁的线程
             * 2. 将 state 设置为 0
             * 3. 清除独占线程引用
             *
             * @param arg 释放的资源数量（通常为 1）
             * @return 是否完全释放（对于非重入锁，总是返回 true）
             */
            @Override
            protected boolean tryRelease(int arg) {
                // 只有持有锁的线程才能释放锁
                if (Thread.currentThread() != getExclusiveOwnerThread()) {
                    throw new IllegalMonitorStateException(
                            "当前线程未持有锁，无法释放");
                }

                // 【释放锁】将 state 重置为 0
                setState(0);

                // 清除独占线程引用
                setExclusiveOwnerThread(null);
                return true; // 通知 AQS 可以唤醒等待队列中的下一个节点
            }

            /**
             * 判断当前线程是否独占锁（用于 Condition 条件变量）
             */
            @Override
            protected boolean isHeldExclusively() {
                return getExclusiveOwnerThread() == Thread.currentThread();
            }

            public java.util.concurrent.locks.Condition createCondition() {
                return new ConditionObject();
            }
        }

        /** 获取锁：委托给 AQS 的 acquire 方法 */
        @Override
        public void lock() {
            /*
             * AQS.acquire(int arg) 的内部流程：
             *
             * 1. 调用 tryAcquire(arg) —— 我们实现的那个方法
             *    ├── 成功 → 直接返回，线程继续执行
             *    └── 失败 → 进入第 2 步
             *
             * 2. 将当前线程封装成 Node，加入 CLH 等待队列尾部
             *
             * 3. 在一个循环中：
             *    a. 检查前驱节点是否是头节点
             *    b. 如果是，再次尝试 tryAcquire
             *    c. 如果不是，或者 tryAcquire 失败：
             *       - 确保前驱节点的 waitStatus = SIGNAL
             *       - 调用 LockSupport.park() 挂起当前线程
             *    d. 被唤醒后，回到步骤 a 重试
             */
            sync.acquire(1);
        }

        /** 释放锁：委托给 AQS 的 release 方法 */
        @Override
        public void unlock() {
            /*
             * AQS.release(int arg) 的内部流程：
             *
             * 1. 调用 tryRelease(arg) —— 我们实现的那个方法
             *
             * 2. 如果 tryRelease 返回 true（完全释放）：
             *    a. 获取头节点的后继节点
             *    b. 如果后继节点的 waitStatus 是 SIGNAL（需要唤醒）：
             *       - 调用 LockSupport.unpark(后继节点.thread)
             *       - 后继节点线程被唤醒，重新竞争锁
             */
            sync.release(1);
        }

        // 以下是为满足 Lock 接口而实现的其他方法（简化版）
        @Override
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        @Override
        public boolean tryLock() {
            return sync.tryAcquire(1);
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(time));
        }

        @Override
        public java.util.concurrent.locks.Condition newCondition() {
            return sync.createCondition();
        }
    }

    /**
     * ============================================================
     * 手写实现：基于 AQS 的简单信号量（共享模式示例）
     * ============================================================
     *
     * 与互斥锁不同，信号量允许多个线程同时持有"许可证"。
     * 这是 AQS 共享模式（Shared）的典型应用。
     *
     * 【独占 vs 共享的关键区别】
     * - 独占：tryAcquire 返回 boolean（true/false）
     * - 共享：tryAcquireShared 返回 int（负数=失败，0/正数=成功）
     */
    static class SimpleSemaphore {

        /** 内部同步器，继承 AQS（共享模式实现） */
        private final Sync sync;

        public SimpleSemaphore(int permits) {
            this.sync = new Sync(permits);
        }

        /**
         * 共享模式的同步器实现
         */
        private static class Sync extends AbstractQueuedSynchronizer {

            Sync(int permits) {
                // 初始化 state 为许可证总数
                setState(permits);
            }

            /**
             * 获取当前可用的许可证数量（public 方法供外部类调用）
             */
            public int getAvailablePermits() {
                return getState();
            }

            /**
             * 以共享模式尝试获取资源
             *
             * 【返回值的含义 —— 非常重要！】
             * - 负数：获取失败，当前线程需要进入等待队列
             * - 零  ：获取成功，但已经没有剩余资源了
             * - 正数：获取成功，还有剩余资源，可以继续唤醒后续共享节点
             *
             * 这个返回值的设计使得 AQS 能够实现"传播唤醒"：
             * 当一个共享节点获取成功且还有剩余资源时，
             * AQS 会继续唤醒后续的共享节点，让它们也来尝试获取。
             *
             * @param arg 要获取的资源数量（通常为 1）
             * @return 剩余资源数量（负数表示失败）
             */
            @Override
            protected int tryAcquireShared(int arg) {
                for (;;) { // 自旋（可能需要多次尝试）
                    int current = getState(); // 读取当前可用许可证数
                    int remaining = current - arg; // 计算获取后剩余的数量

                    // 【情况1】剩余数量 < 0：资源不够，获取失败
                    if (remaining < 0) {
                        return remaining; // 返回负数，AQS 会将线程加入等待队列
                    }

                    // 【情况2】剩余数量 >= 0：尝试 CAS 减少许可证数
                    // 这里用 CAS 保证原子性：防止多个线程同时读到相同的 current 值
                    if (compareAndSetState(current, remaining)) {
                        // CAS 成功：返回剩余数量
                        // - 如果 remaining > 0，AQS 会继续唤醒后续的共享节点
                        // - 如果 remaining == 0，不再唤醒后续节点
                        return remaining;
                    }
                    // CAS 失败：说明有其他线程抢先修改了 state
                    // 重新循环，读取最新的 state 再次尝试
                }
            }

            /**
             * 以共享模式尝试释放资源
             *
             * @param arg 释放的资源数量（通常为 1）
             * @return 是否应该唤醒后继节点
             */
            @Override
            protected boolean tryReleaseShared(int arg) {
                for (;;) { // 自旋（CAS 可能失败需要重试）
                    int current = getState();
                    int next = current + arg; // 计算释放后的新值

                    // 简单的溢出检查（实际生产环境可能需要更严格的校验）
                    if (next < current) {
                        throw new Error("许可证数量上溢（Maximum permit count exceeded）");
                    }

                    // CAS 更新 state：增加许可证数量
                    if (compareAndSetState(current, next)) {
                        // CAS 成功
                        // 返回 true 通知 AQS：有新资源可用，可以唤醒等待节点
                        return true;
                    }
                    // CAS 失败则重试
                }
            }
        }

        /** 获取一个许可证（可能会阻塞） */
        public void acquire() throws InterruptedException {
            /*
             * AQS.acquireSharedInterruptibly(int arg):
             * 类似于独占模式的 acquire，但是：
             * 1. 调用的是 tryAcquireShared（不是 tryAcquire）
             * 2. 获取成功后会传播唤醒其他共享节点
             * 3. 支持响应中断
             */
            sync.acquireSharedInterruptibly(1);
        }

        /** 释放一个许可证 */
        public void release() {
            sync.releaseShared(1);
        }

        /** 查询当前可用的许可证数量 */
        public int availablePermits() {
            return sync.getAvailablePermits();
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [AQS-Demo] %s%n", LocalTime.now(), msg);
    }
}
