# Java 多线程学习项目（JDK 21）

这是一个面向“可运行、可观察、可复用”的 Java 多线程学习项目。

项目把常见并发知识点拆成独立 `main` 示例。每个示例都可以单独运行，适合按知识点观察线程行为、日志顺序、任务拆分、结果合并、阻塞等待、任务取消、锁竞争、原子更新、异步编排、并发队列、并发集合、Fork/Join 工作窃取和结构化并发等场景。

## 1. 运行环境

- JDK：21
- Maven：3.9+
- 源码编码：UTF-8

> 项目包含 `StructuredTaskScope` 示例。`StructuredTaskScope` 在 JDK 21 中仍是 Preview API，编译和运行相关示例都需要 `--enable-preview`。当前 `pom.xml` 已在 `maven-compiler-plugin` 中配置 `--enable-preview`。

## 2. 项目结构

```text
src/main/java/com/example/multithread
├── threadstart              线程创建与任务提交方式
├── threadpool               线程池参数、生命周期与生产场景
├── producerconsumer         生产者 / 消费者模式
├── concurrenttool           JUC 并发工具类
│   ├── phaser               Phaser 多阶段协同
│   ├── exchanger            Exchanger 两线程数据交换
│   └── locksupport          LockSupport park / unpark
├── queue                    JUC 并发队列
│   ├── delayqueue           DelayQueue 延迟队列
│   ├── synchronousqueue     SynchronousQueue 零容量交接队列
│   └── transferqueue        LinkedTransferQueue / TransferQueue
├── completionservice        ExecutorCompletionService 完成顺序消费
├── concurrentcollection     JUC 并发集合
│   ├── skiplists            ConcurrentSkipListMap / ConcurrentSkipListSet
│   └── copyonwrite          CopyOnWriteArrayList / CopyOnWriteArraySet
├── lock                     synchronized、Lock、读写锁、StampedLock
├── atomic                   原子类、原子数组、CAS、LongAdder 与指标统计
│   ├── array                AtomicIntegerArray / AtomicLongArray / AtomicReferenceArray
│   ├── markable             AtomicMarkableReference
│   └── accumulator          LongAccumulator / DoubleAdder / DoubleAccumulator
├── completablefuture        CompletableFuture 异步编排
├── threadlocal              ThreadLocal 上下文隔离与污染风险
├── memory                   happens-before / JMM
│   └── jmm                  JMM 可见性规则专题
├── flow                     Flow / SubmissionPublisher
│   └── submissionpublisher  响应式流发布订阅示例
├── scheduled                ScheduledExecutorService 定时任务
├── virtualthread            JDK 21 虚拟线程
├── structuredconcurrency    JDK 21 结构化并发 Preview
├── forkjoin                 ForkJoinPool、RecursiveTask、RecursiveAction、CountedCompleter
└── principle                CAS / AQS 底层原理示例
```

## 3. 快速开始

### 3.1 编译

```bash
mvn clean compile
```

### 3.2 运行普通示例

```bash
java -cp target/classes com.example.multithread.atomic.AtomicIntegerCounterDemo
```

### 3.3 运行 Preview 示例

结构化并发相关示例需要加 `--enable-preview`：

```bash
java --enable-preview -cp target/classes com.example.multithread.structuredconcurrency.StructuredTaskScopePreviewDemo
```

> Windows 的 classpath 分隔符是 `;`，macOS / Linux 是 `:`。本项目只使用一个 `target/classes` 路径时不涉及分隔符差异。

## 4. 知识点清单

### 4.1 线程基础：`threadstart`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `ThreadStartWaysDemo` | `Thread`、`Runnable`、`Callable`、`FutureTask`、匿名内部类 | 线程启动方式、任务与线程解耦、返回值获取、异常传播 |

```bash
java -cp target/classes com.example.multithread.threadstart.ThreadStartWaysDemo
```

### 4.2 线程池：`threadpool`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `ThreadPoolExecutorParameterDemo` | `corePoolSize`、`maximumPoolSize`、有界队列、拒绝策略 | 线程池扩容、排队、拒绝策略触发顺序 |
| `ExecutorServiceLifecycleDemo` | `execute`、`submit`、`invokeAll`、优雅关闭 | 不同提交方式的差异、`shutdown` / `awaitTermination` 流程 |
| `OrderBatchProcessorDemo` | 订单批处理线程池、自定义线程名、自定义拒绝策略 | 生产场景下如何设置有界队列、命名线程、兜底处理拒绝任务 |

```bash
java -cp target/classes com.example.multithread.threadpool.ThreadPoolExecutorParameterDemo
java -cp target/classes com.example.multithread.threadpool.ExecutorServiceLifecycleDemo
java -cp target/classes com.example.multithread.threadpool.OrderBatchProcessorDemo
```

### 4.3 生产者 / 消费者模式：`producerconsumer`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `ProducerConsumerPatternDemo` | 多生产者、多消费者、有界阻塞队列、背压、毒丸消息 | 生产线程与消费线程解耦、队列满时生产者阻塞、消费者优雅退出 |

```bash
java -cp target/classes com.example.multithread.producerconsumer.ProducerConsumerPatternDemo
```

### 4.4 JUC 并发工具类：`concurrenttool`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `BlockingQueueProducerConsumerDemo` | `BlockingQueue`、生产者消费者、毒丸消息 | `put` / `take` 阻塞、背压、消费者优雅退出 |
| `LogPipelineDemo` | 异步日志流水线、批量消费 | 用队列解耦业务线程和慢 IO，控制队列容量和批次大小 |
| `FutureTimeoutCancelDemo` | `Future#get(timeout)`、`cancel(true)` | 超时等待、任务取消、中断响应 |
| `SemaphoreConcurrencyControlDemo` | `Semaphore`、许可证限流、公平队列 | 同一时刻最大并发数控制、`acquire` / `release` 配对 |
| `CountDownLatchCoordinationDemo` | `CountDownLatch` 一次性协同器 | 主线程等待多个子任务、`await(timeout)` 超时保护 |
| `CountDownLatchStartupDemo` | 系统模块并行启动 | 多模块初始化完成后再对外提供服务 |
| `CyclicBarrierBatchSyncDemo` | `CyclicBarrier` 可循环栅栏 | 多线程分批对齐、`barrierAction` 触发时机 |
| `PhaserBatchTaskDemo` | `Phaser`、多阶段协同、动态注册 / 注销 | 加载、校验、写入这类多阶段任务如何按阶段对齐 |
| `ExchangerDataExchangeDemo` | `Exchanger`、两个线程配对交换数据 | 两个线程交换缓冲区，避免额外复制和中间队列 |
| `LockSupportPermitDemo` | `LockSupport`、`park`、`unpark`、permit | 底层阻塞 / 唤醒机制、先 `unpark` 后 `park`、中断唤醒 |

```bash
java -cp target/classes com.example.multithread.concurrenttool.BlockingQueueProducerConsumerDemo
java -cp target/classes com.example.multithread.concurrenttool.LogPipelineDemo
java -cp target/classes com.example.multithread.concurrenttool.FutureTimeoutCancelDemo
java -cp target/classes com.example.multithread.concurrenttool.SemaphoreConcurrencyControlDemo
java -cp target/classes com.example.multithread.concurrenttool.CountDownLatchCoordinationDemo
java -cp target/classes com.example.multithread.concurrenttool.CountDownLatchStartupDemo
java -cp target/classes com.example.multithread.concurrenttool.CyclicBarrierBatchSyncDemo
java -cp target/classes com.example.multithread.concurrenttool.phaser.PhaserBatchTaskDemo
java -cp target/classes com.example.multithread.concurrenttool.exchanger.ExchangerDataExchangeDemo
java -cp target/classes com.example.multithread.concurrenttool.locksupport.LockSupportPermitDemo
```

Phaser、Exchanger 和 LockSupport 观察点：

1. `Phaser` 适合多阶段协同，比 `CountDownLatch` 更适合“阶段 1 到齐后进入阶段 2，阶段 2 到齐后进入阶段 3”的流程。
2. `Phaser#register()` 和 `arriveAndDeregister()` 可以动态调整参与方数量，适合任务数量运行期才确定的场景。
3. `Exchanger` 只适合两个线程配对交换，任一方先到都会等待另一方。
4. 生产代码使用 `Exchanger` 时通常优先用带超时的 `exchange`，避免配对线程异常退出后永久阻塞。
5. `LockSupport` 是很多 JUC 同步器的底层工具，`unpark` 可以先于 `park` 调用，permit 最多只有一个。
6. `park` 可能因为 `unpark`、中断或少数虚假唤醒返回，真实同步器通常要配合条件循环判断。

### 4.5 JUC 并发队列：`queue`

| 子包 | 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- | --- |
| `delayqueue` | `DelayQueueOrderTimeoutDemo` | `DelayQueue`、`Delayed`、按到期时间出队 | 订单超时关闭、缓存过期、延迟重试 |
| `synchronousqueue` | `SynchronousQueueHandoffDemo` | 零容量队列、生产者消费者直接交接 | `put` 必须等 `take`，元素不会在队列中缓存 |
| `transferqueue` | `LinkedTransferQueueDemo` | `TransferQueue`、`transfer`、`tryTransfer` | 生产者确认元素是否已经被消费者接收 |

```bash
java -cp target/classes com.example.multithread.queue.delayqueue.DelayQueueOrderTimeoutDemo
java -cp target/classes com.example.multithread.queue.synchronousqueue.SynchronousQueueHandoffDemo
java -cp target/classes com.example.multithread.queue.transferqueue.LinkedTransferQueueDemo
```

队列重点观察：

1. `DelayQueue` 的 `take()` 只会取出已经到期的元素，出队顺序由到期时间决定，不由入队顺序决定。
2. `SynchronousQueue` 没有容量，不能缓存元素，适合一手交一手的任务交接。
3. `TransferQueue#transfer` 会等待消费者真实接收元素，语义比普通 `put` 更强。
4. `LinkedTransferQueue` 同时支持普通队列语义和 transfer 语义，适合消费者可能已经在等待、也可能稍后才消费的场景。

### 4.6 ExecutorCompletionService：`completionservice`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `ExecutorCompletionServiceDemo` | `ExecutorCompletionService`、完成队列、按完成顺序消费 | 多个 `Callable` 并发执行后，谁先完成就先处理谁，避免按提交顺序 `get()` 被慢任务拖住 |

```bash
java -cp target/classes com.example.multithread.completionservice.ExecutorCompletionServiceDemo
```

### 4.7 JUC 并发集合：`concurrentcollection`

| 子包 | 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- | --- |
| `skiplists` | `ConcurrentSkipListCollectionDemo` | `ConcurrentSkipListMap`、`ConcurrentSkipListSet`、有序并发集合、范围查询 | 线程安全写入后仍按 key / 元素排序，支持 `subMap`、`tailSet` |
| `copyonwrite` | `CopyOnWriteCollectionDemo` | `CopyOnWriteArrayList`、`CopyOnWriteArraySet`、读写分离、快照迭代 | 读多写少场景，迭代器看到创建时快照，不抛并发修改异常 |

```bash
java -cp target/classes com.example.multithread.concurrentcollection.skiplists.ConcurrentSkipListCollectionDemo
java -cp target/classes com.example.multithread.concurrentcollection.copyonwrite.CopyOnWriteCollectionDemo
```

并发集合重点观察：

1. `ConcurrentSkipListMap` / `ConcurrentSkipListSet` 维护有序结构，适合排行榜、时间线、区间查询。
2. 如果只需要高吞吐 key-value 访问且不关心顺序，通常优先考虑 `ConcurrentHashMap`。
3. `CopyOnWriteArrayList` / `CopyOnWriteArraySet` 适合读多写少，每次写入都会复制底层数组。
4. CopyOnWrite 迭代器是快照语义，遍历期间其他线程写入不会影响当前迭代器。

### 4.8 锁：`lock`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `SynchronizedKeywordDemo` | `synchronized` 实例方法锁、代码块锁、类锁 | 不同锁对象的互斥范围、共享变量保护 |
| `LockInterfaceCriticalSectionDemo` | `Lock` 接口、临界区、`finally` 释放锁 | 显式加锁和释放锁的标准写法 |
| `ReentrantLockAdvancedDemo` | 可重入锁、`tryLock(timeout)`、公平锁 | 同线程重复获取同一把锁、竞争失败后的超时返回 |
| `ReadWriteLockCacheDemo` | `ReadWriteLock`、读写分离 | 读多写少缓存场景下读并发、写互斥 |
| `ConditionBoundedQueueDemo` | `Condition`、`await` / `signal` | 用条件队列实现有界阻塞队列 |
| `StampedLockOptimisticReadDemo` | `StampedLock`、乐观读、戳校验 | 乐观读失败后回退到悲观读，保证数据一致性 |

```bash
java -cp target/classes com.example.multithread.lock.SynchronizedKeywordDemo
java -cp target/classes com.example.multithread.lock.LockInterfaceCriticalSectionDemo
java -cp target/classes com.example.multithread.lock.ReentrantLockAdvancedDemo
java -cp target/classes com.example.multithread.lock.ReadWriteLockCacheDemo
java -cp target/classes com.example.multithread.lock.ConditionBoundedQueueDemo
java -cp target/classes com.example.multithread.lock.StampedLockOptimisticReadDemo
```

### 4.9 原子类与无锁计数：`atomic`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `AtomicIntegerCounterDemo` | `AtomicInteger` 原子计数 | 普通 `int` 丢失更新与原子计数正确性的对比 |
| `AtomicLongSequenceDemo` | `AtomicLong#getAndIncrement` | 并发生成本地唯一、单调递增流水号 |
| `AtomicReferenceConfigSwapDemo` | `AtomicReference`、不可变对象、CAS 替换 | 配置热更新、基于旧值的原子替换、CAS 重试 |
| `LongAdderThroughputDemo` | `LongAdder`、分段累加 | 高并发计数下与 `AtomicLong` 的吞吐差异 |
| `MetricsCounterDemo` | `ConcurrentHashMap` + `LongAdder` | 按 API 维度统计请求数、成功数、失败数 |
| `AtomicArrayDemo` | `AtomicIntegerArray`、`AtomicLongArray`、`AtomicReferenceArray` | 对数组中单个下标做原子更新、CAS 抢占槽位、理解原子数组复制语义 |
| `AtomicMarkableReferenceDemo` | `AtomicMarkableReference`、引用 + boolean 标记位原子更新 | 逻辑删除、有效位标记、用标记位识别引用未变但状态已变的情况 |
| `AccumulatorDemo` | `LongAccumulator`、`DoubleAdder`、`DoubleAccumulator` | 高并发统计总耗时、最大订单金额、最低报价这类指标 |

```bash
java -cp target/classes com.example.multithread.atomic.AtomicIntegerCounterDemo
java -cp target/classes com.example.multithread.atomic.AtomicLongSequenceDemo
java -cp target/classes com.example.multithread.atomic.AtomicReferenceConfigSwapDemo
java -cp target/classes com.example.multithread.atomic.LongAdderThroughputDemo
java -cp target/classes com.example.multithread.atomic.MetricsCounterDemo
java -cp target/classes com.example.multithread.atomic.array.AtomicArrayDemo
java -cp target/classes com.example.multithread.atomic.markable.AtomicMarkableReferenceDemo
java -cp target/classes com.example.multithread.atomic.accumulator.AccumulatorDemo
```

原子数组和标记引用重点观察：

1. `AtomicIntegerArray` / `AtomicLongArray` 保证的是“单个数组下标”的原子更新，不保证多个下标组成的复合操作天然原子。
2. `AtomicReferenceArray` 适合把数组每个槽位当成独立的原子引用，用 `compareAndSet(index, expected, update)` 做无锁抢占或状态切换。
3. 使用普通数组构造原子数组时，原子数组会复制原始数组内容，后续修改原始数组不会影响原子数组。
4. `AtomicMarkableReference` 把引用和 boolean 标记作为一个整体 CAS，适合“引用对象 + 是否删除 / 是否有效”这类二值状态。
5. 如果需要记录更细的版本号，通常使用 `AtomicStampedReference`；如果只需要一个二值标记，`AtomicMarkableReference` 更直接。
6. `DoubleAdder` 适合高并发 double 累加统计，`LongAccumulator` / `DoubleAccumulator` 适合自定义聚合函数。
7. 累加器类的 `sum()` / `get()` 更适合指标快照，不适合账户余额、库存扣减这类强一致业务状态。

### 4.10 CompletableFuture：`completablefuture`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `CompletableFuturePipelineDemo` | `supplyAsync`、`thenCombine`、超时降级、异常兜底 | 多个异步任务编排、结果合并、失败恢复 |
| `PriceComparisonDemo` | 多渠道比价聚合、`allOf`、结果状态建模 | 并发调用多个下游渠道、单渠道超时兜底、过滤成功报价后聚合最优报价 |

```bash
java -cp target/classes com.example.multithread.completablefuture.CompletableFuturePipelineDemo
java -cp target/classes com.example.multithread.completablefuture.PriceComparisonDemo
```

### 4.11 ThreadLocal：`threadlocal`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `TraceContextDemo` | `ThreadLocal`、`InheritableThreadLocal`、线程池上下文污染 | 线程隔离、子线程继承、线程复用导致的脏上下文风险 |

```bash
java -cp target/classes com.example.multithread.threadlocal.TraceContextDemo
```

### 4.12 happens-before / JMM：`memory`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `HappensBeforeJmmDemo` | JMM、happens-before、`volatile`、`Thread.start`、`Thread.join` | 正确建立可见性关系后，一个线程的写入如何对另一个线程可见 |

```bash
java -cp target/classes com.example.multithread.memory.jmm.HappensBeforeJmmDemo
```

JMM 重点观察：

1. `volatile` 写 happens-before 后续对同一个 volatile 变量的读，常用于发布配置、停止标记等场景。
2. `Thread.start()` 之前的写入，对新线程启动后的操作可见。
3. 一个线程内的所有操作 happens-before 其他线程成功从该线程的 `join()` 返回。
4. JMM 示例重点是建立正确规则，不建议依赖“偶发复现”的可见性 bug 作为稳定教学输出。

### 4.13 Flow / SubmissionPublisher：`flow`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `SubmissionPublisherFlowDemo` | `Flow.Publisher`、`Flow.Subscriber`、`Flow.Subscription`、`SubmissionPublisher` | 发布订阅、异步分发、订阅者通过 `request(n)` 控制需求量 |

```bash
java -cp target/classes com.example.multithread.flow.submissionpublisher.SubmissionPublisherFlowDemo
```

Flow 重点观察：

1. `SubmissionPublisher` 是 JDK 自带的基础 `Publisher` 实现，适合学习 Flow 接口和简单发布订阅。
2. `Subscriber#onSubscribe` 中必须保存 `Subscription` 并调用 `request(n)`，否则不会收到数据。
3. `request(n)` 是响应式流背压入口，消费者可以按自身处理能力逐步请求数据。
4. `onComplete` 表示发布者已关闭并且数据发送完毕，`onError` 表示流处理失败。

### 4.14 定时任务：`scheduled`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `HealthCheckSchedulerDemo` | `ScheduledExecutorService`、固定频率任务、优雅停机 | 周期性健康检查、关闭钩子、调度线程池退出 |

```bash
java -cp target/classes com.example.multithread.scheduled.HealthCheckSchedulerDemo
```

### 4.15 虚拟线程：`virtualthread`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `VirtualThreadApiAggregationDemo` | JDK 21 虚拟线程、每任务一个虚拟线程、`Thread.ofVirtual` | 虚拟线程创建方式、适合 IO 阻塞型任务的并发模型 |

```bash
java -cp target/classes com.example.multithread.virtualthread.VirtualThreadApiAggregationDemo
```

### 4.16 Fork/Join：`forkjoin`

| 子包 | 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- | --- |
| `forkjoinpool` | `ForkJoinPoolBasicDemo` | `ForkJoinPool`、`fork`、`join`、自定义并行度 | 大任务拆分、小任务计算、结果合并、池状态指标 |
| `recursivetask` | `RecursiveTaskSumDemo` | `RecursiveTask<V>` 有返回值分治任务 | 数组求和这类“拆分后需要合并结果”的任务 |
| `recursiveaction` | `RecursiveActionImageDemo` | `RecursiveAction` 无返回值分治任务 | 批量修改数组、图片像素处理这类副作用任务 |
| `countedcompleter` | `CountedCompleterSearchDemo` | `CountedCompleter`、pending count、提前完成 | 子任务不逐个 `join`，通过完成计数和共享结果协作 |
| `workstealing` | `WorkStealingModelDemo` | 工作窃取模型、双端队列、`stealCount` | 负载不均衡时多个 worker 如何共同消化任务 |
| `commonpool` | `CommonPoolDemo` | `ForkJoinPool.commonPool()`、默认共享池 | `CompletableFuture.supplyAsync` 默认线程池行为 |
| `managedblocker` | `ManagedBlockerDemo` | `ForkJoinPool.ManagedBlocker`、受控阻塞 | ForkJoin 任务里存在阻塞等待时如何通知池做补偿 |

```bash
java -cp target/classes com.example.multithread.forkjoin.forkjoinpool.ForkJoinPoolBasicDemo
java -cp target/classes com.example.multithread.forkjoin.recursivetask.RecursiveTaskSumDemo
java -cp target/classes com.example.multithread.forkjoin.recursiveaction.RecursiveActionImageDemo
java -cp target/classes com.example.multithread.forkjoin.countedcompleter.CountedCompleterSearchDemo
java -cp target/classes com.example.multithread.forkjoin.workstealing.WorkStealingModelDemo
java -cp target/classes com.example.multithread.forkjoin.commonpool.CommonPoolDemo
java -cp target/classes com.example.multithread.forkjoin.managedblocker.ManagedBlockerDemo
```

### 4.17 结构化并发：`structuredconcurrency`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `StructuredTaskScopePreviewDemo` | `StructuredTaskScope.ShutdownOnFailure` | 子任务同生共死、任一失败后整体失败、结果聚合 |
| `StructuredConcurrencyVirtualThreadDemo` | 结构化并发 + 默认虚拟线程 + 订单详情聚合 | `fork` / `join` / `throwIfFailed` 流程、失败时取消其他子任务、与 `CompletableFuture` 的机制对比 |
| `ShutdownOnSuccessDemo` | `StructuredTaskScope.ShutdownOnSuccess`、竞速模式 | 多供应商竞速，最快正常返回的结果被 `result()` 选中后取消其他任务 |

```bash
java --enable-preview -cp target/classes com.example.multithread.structuredconcurrency.StructuredTaskScopePreviewDemo
java --enable-preview -cp target/classes com.example.multithread.structuredconcurrency.StructuredConcurrencyVirtualThreadDemo
java --enable-preview -cp target/classes com.example.multithread.structuredconcurrency.ShutdownOnSuccessDemo
```

### 4.18 并发底层原理：`principle`

| 示例类 | 核心知识点 | 适合观察 |
| --- | --- | --- |
| `CASPrincipleDemo` | CAS 思想、比较并交换、无锁更新 | 非原子更新丢失问题、CAS 循环重试如何保证正确性 |
| `AQSPrincipleDemo` | AQS、同步状态、CLH 队列、独占 / 共享模式 | 手写非可重入互斥锁和共享式信号量，理解 JUC 同步器基础 |

```bash
java -cp target/classes com.example.multithread.principle.CASPrincipleDemo
java -cp target/classes com.example.multithread.principle.AQSPrincipleDemo
```

## 5. 推荐学习路线

1. 先运行 `ThreadStartWaysDemo`，理解线程、任务、返回值和 `FutureTask` 的基础关系。
2. 再看 `threadpool`，重点理解线程池不是“越大越好”，核心参数、队列容量和拒绝策略必须一起设计。
3. 单独运行 `producerconsumer`，理解生产线程和消费线程如何通过有界队列解耦，以及背压和优雅停机为什么重要。
4. 继续看 `concurrenttool`，掌握阻塞队列、信号量、闭锁、栅栏、`Phaser`、`Exchanger`、`LockSupport` 和 `Future` 超时取消这些常用协作工具。
5. 然后看 `queue`，理解 `DelayQueue`、`SynchronousQueue`、`TransferQueue` 在交付语义上的差异。
6. 再看 `completionservice`，理解“按完成顺序消费结果”与“按提交顺序等待结果”的差异。
7. 接着看 `concurrentcollection`，对比有序并发集合和 CopyOnWrite 集合各自适合的读写模型。
8. 再看 `lock`，对比 `synchronized`、`Lock`、`ReadWriteLock`、`StampedLock` 各自解决的问题。
9. 接着看 `atomic` 和 `principle`，从原子计数、原子引用、原子数组、标记引用、累加器过渡到 CAS / AQS 原理，理解 JUC 的底层支撑。
10. 单独看 `memory`，用 happens-before 规则理解可见性和有序性问题。
11. 再看 `forkjoin`，理解分治任务、工作窃取、公共池和阻塞补偿机制。
12. 最后看 `CompletableFuture`、`flow`、虚拟线程和结构化并发，理解现代 Java 中更适合业务编排、发布订阅和高并发 IO 的写法。

## 6. 观察与实验建议

1. 每次只运行一个示例，先读类顶部注释，再看控制台输出。
2. 修改线程数、循环次数、队列容量、任务阈值、超时时间，观察吞吐、等待、拒绝、取消和日志顺序变化。
3. 对比不同队列的交付语义：`DelayQueue` 按时间交付，`SynchronousQueue` 直接交接，`TransferQueue` 可以等待消费者真实接收。
4. 对比不同集合的读写模型：跳表集合维护顺序，CopyOnWrite 集合读快写贵。
5. 对比不同可见性手段：`volatile`、`start`、`join`、锁释放 / 获取都能建立不同的 happens-before 关系。
6. 关注中断处理：看到 `InterruptedException` 时，示例通常会调用 `Thread.currentThread().interrupt()` 恢复中断标记。
7. 关注资源释放：线程池、锁、信号量、`SubmissionPublisher`、结构化并发作用域都要有明确的关闭或释放路径。
