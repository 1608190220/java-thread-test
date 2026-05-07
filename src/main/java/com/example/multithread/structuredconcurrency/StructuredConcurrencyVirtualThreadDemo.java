package com.example.multithread.structuredconcurrency;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 知识点：结构化并发 + 虚拟线程的组合使用（JDK 21 Preview，现代 Java 并发编程最佳实践）。
 *
 * 核心概念：
 * =========
 *
 * 【为什么需要虚拟线程？】
 * 传统平台线程（Platform Thread）的痛点：
 * - 线程数量有限（通常几千个就是上限）
 * - 线程上下文切换开销大（约几微秒到几十微秒）
 * - 阻塞操作（IO等待）会占用线程资源，无法服务其他请求
 * - 对于高并发IO密集型应用（如Web服务），容易成为瓶颈
 *
 * 虚拟线程（Virtual Thread）的解决方案：
 * - 轻量级：可以创建数百万个虚拟线程（内存占用极小）
 * - 调度在JVM层面完成，由调度器（ForkJoinPool）挂载到载体线程上
 * - 阻塞操作时自动卸载，不占用载体线程
 * - API完全兼容：代码无需大改，只需更换线程创建方式
 *
 * 【为什么需要结构化并发？】
 * 使用 CompletableFuture 或手动管理线程池的问题：
 * - 子任务的生命周期难以追踪（可能泄漏）
 * - 错误处理分散且复杂（需要手动组合异常）
 * - 取消联动困难（取消父任务时，子任务不一定能及时停止）
 * - 代码可读性差（回调地狱或复杂的链式调用）
 *
 * 结构化并发（StructuredTaskScope）的解决方案：
 * - 子任务生命周期限定在作用域内（try-with-resources）
 * - 统一的错误传播机制（throwIfFailed / result）
 * - 自动取消联动（shutdown 时所有子任务收到中断信号）
 * - 代码清晰直观（顺序编写，并发执行）
 *
 * 【两者结合 = 最佳实践】
 * 虚拟线程 + 结构化并发 = 现代 Java 并发编程的理想模式：
 * - 大量IO密集型任务 → 用虚拟线程避免资源耗尽
 * - 任务生命周期管理 → 用结构化并发确保正确性和可读性
 * - JDK 21 的推荐模式，也是 Loom 项目的核心目标
 *
 * 典型应用场景：
 * ============
 * 本例模拟电商订单详情页接口的数据聚合：
 * 一个订单详情页面需要同时查询5个独立服务：
 *   1. 用户基本信息服务（用户名、等级、头像等）
 *   2. 商品详情服务（商品名称、价格、规格等）
 *   3. 优惠券服务（可用优惠券列表）
 *   4. 库存服务（库存数量、是否缺货）
 *   5. 物流预估服务（预计送达时间）
 *
 * 这5个查询相互独立，可以并行执行，最后聚合结果返回给前端。
 * 使用结构化并发 + 虚拟线程可以：
 *   - 同时发起5个并行查询（利用虚拟线程的低成本特性）
 *   - 自动管理这5个子任务的生命周期（结构化并发的优势）
 *   - 任一失败时快速取消其他任务（ShutdownOnFailure策略）
 *   - 代码简洁易读（比 CompletableFuture 链式调用更直观）
 *
 * 演示内容：
 * ==========
 * Part 1: 正常情况 - 所有子任务成功，展示结果聚合
 * Part 2: 异常情况 - 某个超时/失败，展示自动取消行为
 * Part 3: 对比说明 - 与传统 CompletableFuture 方式的差异
 *
 * 注意：
 * 1. 该 API 在 JDK 21 中是 Preview 特性。
 * 2. 需要使用 --enable-preview 编译和运行。
 * 3. StructuredTaskScope 默认使用虚拟线程执行子任务。
 *
 * 编译命令（在项目根目录执行）：
 * $env:JAVA_HOME="D:\JDK\openjdk21\jdk-21"; mvn clean compile 2>&1
 *
 * 运行命令：
 * $env:JAVA_HOME="D:\JDK\openjdk21\jdk-21"; java --enable-preview -cp target/classes com.example.multithread.structuredconcurrency.StructuredConcurrencyVirtualThreadDemo
 */
public class StructuredConcurrencyVirtualThreadDemo {

    public static void main(String[] args) throws Exception {
        log("============================================================");
        log("  结构化并发 + 虚拟线程 组合使用演示");
        log("  场景：电商订单详情页数据聚合");
        log("============================================================");
        log("");

        // ========== Part 1: 正常情况演示 ==========
        log("========== Part 1: 正常情况 - 所有服务响应正常 ==========");
        demonstrateNormalCase();

        log("");
        log("");

        // ========== Part 2: 异常情况演示 ==========
        log("========== Part 2: 异常情况 - 某个服务超时/失败 ==========");
        demonstrateFailureCase();

        log("");
        log("");

        // ========== Part 3: 对比说明 ==========
        log("========== Part 3: 结构化并发 vs CompletableFuture 对比 ==========");
        printComparisonSummary();

        log("");
        log("============================================================");
        log("  演示结束");
        log("============================================================");
    }

    /**
     * Part 1: 演示正常情况下的订单详情聚合
     *
     * 流程说明：
     * 1. 创建 ShutdownOnFailure 类型的 StructuredTaskScope
     *    （因为我们需要所有5个服务都成功才能组装完整的订单详情）
     * 2. 使用 fork() 提交5个独立的查询任务
     * 3. 调用 join() 等待所有任务完成
     * 4. 调用 throwIfFailed() 检查是否有失败
     * 5. 从各个 Subtask 获取结果并组装成 OrderDetail
     */
    private static void demonstrateNormalCase() throws Exception {
        String orderId = "ORD-20240115-8866";

        log("开始查询订单详情，订单号：" + orderId);

        // 创建 ShutdownOnFailure 作用域。
        // 选择 ShutdownOnFailure 的原因：
        //   订单详情需要5个服务的完整数据，任何一个失败都无法组装完整信息，
        //   因此采用"全部成功才算成功"的策略。
        //   如果某个服务失败，立即取消其他还在运行的查询，避免浪费资源。
        try (StructuredTaskScope.ShutdownOnFailure scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // ====== 步骤1：fork 5个并行查询任务 ======
            // 每个 fork() 调用都会：
            //   - 创建一个新的虚拟线程（默认行为）
            //   - 在该虚拟线程中异步执行传入的 lambda 表达式
            //   - 立即返回一个 Subtask<T> 对象，用于后续获取结果

            log("【fork】提交5个并行查询任务（每个任务运行在独立虚拟线程中）...");

            // 子任务1：查询用户基本信息
            StructuredTaskScope.Subtask<UserInfo> userTask = scope.fork(() -> {
                log("  [子任务] 开始查询用户信息...");
                UserInfo user = queryUserInfo("U1001");
                log("  [子任务] 用户信息查询完成");
                return user;
            });

            // 子任务2：查询商品详情
            StructuredTaskScope.Subtask<ProductInfo> productTask = scope.fork(() -> {
                log("  [子任务] 开始查询商品详情...");
                ProductInfo product = queryProductInfo("P2001");
                log("  [子任务] 商品详情查询完成");
                return product;
            });

            // 子任务3：查询可用优惠券
            StructuredTaskScope.Subtask<CouponList> couponTask = scope.fork(() -> {
                log("  [子任务] 开始查询优惠券...");
                CouponList coupons = queryCoupons("U1001", orderId);
                log("  [子任务] 优惠券查询完成");
                return coupons;
            });

            // 子任务4：查询库存状态
            StructuredTaskScope.Subtask<InventoryStatus> inventoryTask = scope.fork(() -> {
                log("  [子任务] 开始查询库存...");
                InventoryStatus inventory = queryInventory("P2001");
                log("  [子任务] 库存查询完成");
                return inventory;
            });

            // 子任务5：查询物流预估
            StructuredTaskScope.Subtask<ShippingEstimate> shippingTask = scope.fork(() -> {
                log("  [子任务] 开始查询物流预估...");
                ShippingEstimate shipping = queryShippingEstimate(orderId, "100001");
                log("  [子任务] 物流预估查询完成");
                return shipping;
            });

            // ====== 步骤2：join() 等待所有子任务完成 ======
            log("【join】等待所有子任务完成（主线程阻塞等待）...");
            long startTime = System.currentTimeMillis();
            scope.join();
            long elapsed = System.currentTimeMillis() - startTime;
            log("【join】所有子任务已完成，总耗时：" + elapsed + "ms（注意：这是并行执行的总时间，非串行累加）");

            // ====== 步骤3：检查是否有失败 ======
            log("【throwIfFailed】检查子任务执行结果...");
            scope.throwIfFailed();
            log("【throwIfFailed】所有子任务均成功执行 ✓");

            // ====== 步骤4：获取结果并组装 ======
            log("【聚合】从各子任务获取结果，组装订单详情...");

            OrderDetail orderDetail = new OrderDetail(
                    orderId,
                    userTask.get(),           // 用户信息
                    productTask.get(),        // 商品详情
                    couponTask.get(),         // 优惠券列表
                    inventoryTask.get(),      // 库存状态
                    shippingTask.get()        // 物流预估
            );

            // 输出完整的订单详情
            printOrderDetail(orderDetail);
        }
    }

    /**
     * Part 2: 演示异常情况 - 某个服务超时或失败
     *
     * 这里模拟库存服务响应超时的场景，
     * 展示 ShutdownOnFailure 如何自动取消其他正在运行的子任务。
     */
    private static void demonstrateFailureCase() throws Exception {
        String orderId = "ORD-20240115-9999";

        log("开始查询订单详情（模拟异常场景），订单号：" + orderId);
        log("⚠️  注意：本次查询中，库存服务将模拟超时失败");

        try (StructuredTaskScope.ShutdownOnFailure scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // 提交5个查询任务，其中库存任务会故意抛出异常
            log("【fork】提交5个并行查询任务...");

            StructuredTaskScope.Subtask<UserInfo> userTask = scope.fork(() -> {
                log("  [子任务] 开始查询用户信息...");
                Thread.sleep(300);  // 模拟延迟
                UserInfo user = new UserInfo("U1001", "李四", "黄金会员", "https://avatar.example.com/li4.jpg");
                log("  [子任务] 用户信息查询完成 ✓");
                return user;
            });

            StructuredTaskScope.Subtask<ProductInfo> productTask = scope.fork(() -> {
                log("  [子任务] 开始查询商品详情...");
                Thread.sleep(400);  // 模拟延迟
                ProductInfo product = new ProductInfo("P2001", "高性能无线耳机 Pro Max", 299.00, "黑色");
                log("  [子任务] 商品详情查询完成 ✓");
                return product;
            });

            StructuredTaskScope.Subtask<CouponList> couponTask = scope.fork(() -> {
                log("  [子任务] 开始查询优惠券...");
                Thread.sleep(250);  // 模拟延迟
                CouponList coupons = new CouponList(List.of("满299减30", "新人专享95折"));
                log("  [子任务] 优惠券查询完成 ✓");
                return coupons;
            });

            // ⚠️ 这个任务会模拟超时失败
            StructuredTaskScope.Subtask<InventoryStatus> inventoryTask = scope.fork(() -> {
                log("  [子任务] 开始查询库存...");
                log("  [子任务] ⚠️ 库存服务响应缓慢，等待中...");
                Thread.sleep(1500);  // 模拟很慢的响应（超过预期）

                // 模拟超时后抛出异常
                throw new RuntimeException("库存服务连接超时（TimeoutException）");
            });

            StructuredTaskScope.Subtask<ShippingEstimate> shippingTask = scope.fork(() -> {
                log("  [子任务] 开始查询物流预估...");
                try {
                    Thread.sleep(600);  // 模拟延迟（这个任务可能会被提前取消）

                    // 检查是否被中断（被 ShutdownOnFailure 取消时）
                    if (Thread.currentThread().isInterrupted()) {
                        log("  [子任务] ⚠️ 物流查询检测到中断信号，任务被取消");
                        return new ShippingEstimate("未知", -1);
                    }

                    ShippingEstimate shipping = new ShippingEstimate("顺丰速运", 2);
                    log("  [子任务] 物流预估查询完成 ✓");
                    return shipping;
                } catch (InterruptedException e) {
                    log("  [子任务] ✗ 物流查询被中断：" + e.getMessage());
                    throw e;
                }
            });

            // 等待所有子任务完成
            log("【join】等待所有子任务完成...");
            long startTime = System.currentTimeMillis();

            try {
                scope.join();
                scope.throwIfFailed();  // 这里会抛出异常，因为库存任务失败了
            } catch (ExecutionException e) {
                long elapsed = System.currentTimeMillis() - startTime;
                log("【异常捕获】检测到子任务失败！");
                log("  失败原因：" + e.getCause().getMessage());
                log("  总耗时：" + elapsed + "ms（因失败而提前终止，未等待慢任务完成）");
                log("");
                log("  关键观察点：");
                log("  ✓ 库存服务失败后，其他正在运行的子任务收到了取消信号");
                log("  ✓ 不需要手动逐个取消，结构化并发自动处理");
                log("  ✓ 资源得到及时释放，没有无谓的等待");

                // 可以在这里实现降级逻辑，例如：
                // - 返回部分可用的数据
                // - 使用缓存中的旧数据
                // - 返回友好的错误提示给用户
                log("");
                log("  【降级建议】：在实际业务中，这里可以返回部分已获取的数据 + 缺失提示");
            }
        }
    }

    /**
     * Part 3: 打印结构化并发与传统 CompletableFuture 的对比总结
     */
    private static void printComparisonSummary() {
        log("");
        log("┌─────────────────────────────────────────────────────────────────────┐");
        log("│              结构化并发 vs CompletableFuture 对比                  │");
        log("├──────────────────────┬──────────────────────────────────────────────┤");
        log("│       维度          │           说明                                │");
        log("├──────────────────────┼──────────────────────────────────────────────┤");
        log("│ 代码可读性          │ 结构化并发：顺序编写，一目了然                   │");
        log("│                      │ CompletableFuture：链式调用，嵌套较深          │");
        log("├──────────────────────┼──────────────────────────────────────────────┤");
        log("│ 错误处理            │ 结构化并发：统一的 throwIfFailed()             │");
        log("│                      │ CF：需要 exceptionally()/handle() 分散处理    │");
        log("├──────────────────────┼──────────────────────────────────────────────┤");
        log("│ 取消联动            │ 结构化并发：shutdown() 自动取消所有子任务        │");
        log("│                      │ CF：需要手动 cancel() 每个 Future             │");
        log("├──────────────────────┼──────────────────────────────────────────────┤");
        log("│ 生命周期管理        │ 结构化并发：try-with-resources 自动清理         │");
        log("│                      │ CF：需要自己管理 Future 列表                 │");
        log("├──────────────────────┼──────────────────────────────────────────────┤");
        log("│ 线程模型            │ 结构化并发：默认使用虚拟线程（JDK 21）           │");
        log("│                      │ CF：默认使用 ForkJoinPool.commonPool()       │");
        log("├──────────────────────┼──────────────────────────────────────────────┤");
        log("│ 适用场景            │ 结构化并发：父子关系明确的并发任务               │");
        log("│                      │ CF：更灵活的任务组合（非严格父子关系）          │");
        log("└──────────────────────┴──────────────────────────────────────────────┘");
        log("");
        log("结论：对于'一个请求需要并行调用多个依赖服务'的场景，");
        log("      结构化并发 + 虚拟线程是 JDK 21+ 推荐的最佳实践。");
    }

    /**
     * 打印完整的订单详情信息
     */
    private static void printOrderDetail(OrderDetail detail) {
        log("");
        log("╔══════════════════════════════════════════════════════════════╗");
        log("║                    订单详情（聚合结果）                         ║");
        log("╠══════════════════════════════════════════════════════════════╣");
        log("║ 订单号：" + detail.orderId);
        log("╠══════════════════════════════════════════════════════════════╣");
        log("║ 【用户信息】                                                ║");
        log("║   用户ID：" + detail.user.userId);
        log("║   姓名：" + detail.user.name);
        log("║   等级：" + detail.user.memberLevel);
        log("╠══════════════════════════════════════════════════════════════╣");
        log("║ 【商品信息】                                                ║");
        log("║   商品ID：" + detail.product.productId);
        log("║   名称：" + detail.product.name);
        log("║   价格：¥" + String.format("%.2f", detail.product.price));
        log("║   规格：" + detail.product.specification);
        log("╠══════════════════════════════════════════════════════════════╣");
        log("║ 【优惠券】                                                  ║");
        for (String coupon : detail.coupons.couponList) {
            log("║   • " + coupon);
        }
        log("╠══════════════════════════════════════════════════════════════╣");
        log("║ 【库存状态】                                                ║");
        log("║   库存数量：" + detail.inventory.quantity + " 件");
        log("║   状态：" + (detail.inventory.inStock ? "有货 ✓" : "缺货 ✗"));
        log("╠══════════════════════════════════════════════════════════════╣");
        log("║ 【物流预估】                                                ║");
        log("║   配送方式：" + detail.shipping.carrier);
        if (detail.shipping.estimatedDays > 0) {
            log("║   预计送达：约 " + detail.shipping.estimatedDays + " 天内");
        } else {
            log("║   预计送达：无法估算");
        }
        log("╚══════════════════════════════════════════════════════════════╝");
    }

    // ==================== 模拟服务调用方法 ====================

    /**
     * 模拟查询用户基本信息服务
     * 响应时间：约300ms
     */
    private static UserInfo queryUserInfo(String userId) throws InterruptedException {
        Thread.sleep(300);
        return new UserInfo(userId, "张三", "VIP会员", "https://avatar.example.com/zs.jpg");
    }

    /**
     * 模拟查询商品详情服务
     * 响应时间：约400ms
     */
    private static ProductInfo queryProductInfo(String productId) throws InterruptedException {
        Thread.sleep(400);
        return new ProductInfo(productId, "智能手机 Ultra Pro Max 256GB", 5999.00, "星空黑 256GB");
    }

    /**
     * 模拟查询优惠券服务
     * 响应时间：约200ms
     */
    private static CouponList queryCoupons(String userId, String orderId) throws InterruptedException {
        Thread.sleep(200);
        // 随机生成1-3张优惠券
        int count = 1 + ThreadLocalRandom.current().nextInt(3);
        List<String> coupons = List.of("满5000减300", "会员专享98折", "新品首单立减100");
        return new CouponList(coupons.subList(0, Math.min(count, coupons.size())));
    }

    /**
     * 模拟查询库存服务
     * 响应时间：约350ms
     */
    private static InventoryStatus queryInventory(String productId) throws InterruptedException {
        Thread.sleep(350);
        int quantity = 50 + ThreadLocalRandom.current().nextInt(100);
        return new InventoryStatus(productId, quantity, quantity > 0);
    }

    /**
     * 模拟查询物流预估服务
     * 响应时间：约500ms
     */
    private static ShippingEstimate queryShippingEstimate(String orderId, String zipCode) throws InterruptedException {
        Thread.sleep(500);
        String[] carriers = {"顺丰速运", "京东快递", "圆通速递"};
        String carrier = carriers[ThreadLocalRandom.current().nextInt(carriers.length)];
        int days = 1 + ThreadLocalRandom.current().nextInt(3);
        return new ShippingEstimate(carrier, days);
    }

    // ==================== 数据类定义 ====================

    /**
     * 用户基本信息
     */
    private static record UserInfo(String userId, String name, String memberLevel, String avatarUrl) {
    }

    /**
     * 商品详细信息
     */
    private static record ProductInfo(String productId, String name, double price, String specification) {
    }

    /**
     * 优惠券列表
     */
    private static record CouponList(List<String> couponList) {
    }

    /**
     * 库存状态信息
     */
    private static record InventoryStatus(String productId, int quantity, boolean inStock) {
    }

    /**
     * 物流预估信息
     */
    private static record ShippingEstimate(String carrier, int estimatedDays) {
    }

    /**
     * 聚合后的完整订单详情
     * 包含了从5个独立服务查询到的所有信息
     */
    private static record OrderDetail(
            String orderId,
            UserInfo user,
            ProductInfo product,
            CouponList coupons,
            InventoryStatus inventory,
            ShippingEstimate shipping
    ) {
    }

    /**
     * 统一日志输出方法
     * 格式：[时间] [线程名] 消息内容
     * 通过线程名可以观察到虚拟线程的命名规则（类似 "ForkJoinPool-x-worker-y"）
     */
    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
