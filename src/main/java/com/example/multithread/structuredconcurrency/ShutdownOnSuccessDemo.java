package com.example.multithread.structuredconcurrency;

import java.time.LocalTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 知识点：StructuredTaskScope.ShutdownOnSuccess（JDK 21 Preview，结构化并发 - 竞速模式）。
 *
 * 核心概念：
 * =========
 * ShutdownOnSuccess 是结构化并发的另一种策略模式，与 ShutdownOnFailure 互为补充：
 *
 * - **ShutdownOnFailure**（失败优先）：
 *   任一子任务失败 → 取消其他所有子任务 → 适用于"全部成功才算成功"的场景
 *
 * - **ShutdownOnSuccess**（成功优先/竞速模式）：
 *   任一子任务成功完成 → 立即取消其余子任务 → 适用于"谁快用谁"的竞速场景
 *
 * 典型应用场景：
 * ============
 * 1. 多供应商询价：同时向多个供应商询价，谁先返回就用谁的报价，取消其他慢请求
 * 2. 多 CDN 源站选择：同时从多个源站获取资源，哪个先返回就用哪个
 * 3. 多缓存层查询：同时查询 L1/L2/L3 缓存，任一命中立即返回
 * 4. 服务降级：主服务 + 备用服务竞速，提高响应速度
 *
 * 工作机制：
 * ========
 * 1. 创建 ShutdownOnSuccess<T> 作用域（T 是期望的结果类型）
 * 2. 使用 scope.fork() 提交多个并发子任务
 * 3. 调用 scope.join() 等待所有子任务结束
 * 4. 第一个成功完成的子任务会触发 shutdown()，取消其余子任务
 * 5. 使用 scope.result() 获取第一个成功的结果
 * 6. 如果所有子任务都失败了，scope.result() 会抛出异常
 *
 * 注意：
 * 1. 该 API 在 JDK 21 中是 Preview 特性。
 * 2. 需要使用 --enable-preview 编译和运行。
 * 3. "成功"指的是子任务正常返回结果（未抛异常），而非业务逻辑上的成功。
 *
 * 编译命令（在项目根目录执行）：
 * $env:JAVA_HOME="D:\JDK\openjdk21\jdk-21"; mvn clean compile 2>&1
 *
 * 运行命令：
 * $env:JAVA_HOME="D:\JDK\openjdk21\jdk-21"; java --enable-preview -cp target/classes com.example.multithread.structuredconcurrency.ShutdownOnSuccessDemo
 */
public class ShutdownOnSuccessDemo {

    public static void main(String[] args) throws Exception {
        log("===== 结构化并发 - ShutdownOnSuccess 竞速模式演示 =====");
        log("场景：同时向3个供应商询价，谁先返回就用谁的结果");

        // 创建 ShutdownOnSuccess<Quote> 作用域。
        // 泛型参数 Quote 表示我们期望获取的返回值类型。
        // ShutdownOnSuccess 的核心行为：
        //   当第一个子任务成功完成时，自动调用 scope.shutdown()，
        //   向其他还在运行的子任务发送中断信号，实现"赢家通吃"的竞速效果。
        try (StructuredTaskScope.ShutdownOnSuccess<Quote> scope = new StructuredTaskScope.ShutdownOnSuccess<>()) {

            log("开始向3个供应商发起询价请求...");

            // 使用 fork() 提交3个询价任务到不同的供应商。
            // fork() 会立即返回 Subtask 对象，子任务在后台虚拟线程中异步执行。
            // 每个供应商有不同的响应延迟，模拟真实世界的网络差异：
            //   - 供应商A（快速）：约200ms 返回
            //   - 供应商B（中等）：约500ms 返回
            //   - 供应商C（较慢）：约800ms 返回

            StructuredTaskScope.Subtask<Quote> supplierATask = scope.fork(() -> querySupplierA());
            StructuredTaskScope.Subtask<Quote> supplierBTask = scope.fork(() -> querySupplierB());
            StructuredTaskScope.Subtask<Quote> supplierCTask = scope.fork(() -> querySupplierC());

            log("已提交3个询价任务，等待响应（竞速中）...");

            // join() 阻塞等待所有子任务执行完毕。
            // 关键行为：
            //   - 如果某个子任务先成功返回，ShutdownOnSuccess 会立即触发 shutdown()
            //   - 其他正在运行的子任务会被中断（收到 InterruptedException）
            //   - join() 会在所有子任务都结束后返回（无论成功、失败还是被取消）
            scope.join();

            // result() 获取第一个成功完成的子任务的结果。
            // 注意事项：
            //   - 如果至少有一个子任务成功，返回第一个成功的 Quote 对象
            //   - 如果所有子任务都失败了，抛出 ExecutionException（包含最后一个异常）
            //   - 这里不需要调用 throwIfFailed()，因为我们的目标是"只要一个成功就行"
            Quote winningQuote = scope.result();

            // 输出竞速获胜者的信息
            log("========================================");
            log("竞速结果：使用 [" + winningQuote.supplier + "] 的报价！");
            log("  供应商：" + winningQuote.supplier);
            log("  报价价格：" + String.format("%.2f", winningQuote.price) + " 元");
            log("  交货周期：" + winningQuote.deliveryDays + " 天");
            log("========================================");
        }
        // try-with-resources 结束时自动调用 scope.close()，
        // 确保所有子线程都已清理完毕。

        log("");
        log("===== 演示结束：ShutdownOnSuccess 实现了'多渠道竞速'模式 =====");
    }

    /**
     * 模拟向供应商A询价（最快的供应商，约200ms响应）
     *
     * @return 包含供应商名称、价格和交货周期的报价对象
     */
    private static Quote querySupplierA() throws InterruptedException {
        // 模拟网络延迟：供应商A响应最快（200ms左右）
        Thread.sleep(200);

        // 生成随机报价（99.0 ~ 129.0元之间）
        double price = 99.0 + ThreadLocalRandom.current().nextDouble(30.0);

        log("供应商A 返回报价：¥" + String.format("%.2f", price));
        return new Quote("供应商A-极速达", price, 1);
    }

    /**
     * 模拟向供应商B询价（中等速度的供应商，约500ms响应）
     *
     * @return 包含供应商名称、价格和交货周期的报价对象
     */
    private static Quote querySupplierB() throws InterruptedException {
        // 模拟网络延迟：供应商B响应中等（500ms左右）
        Thread.sleep(500);

        try {
            // 检查当前线程是否被中断（可能被 ShutdownOnSuccess 取消）
            if (Thread.currentThread().isInterrupted()) {
                log("供应商B 检测到中断信号，停止处理");
                return new Quote("供应商B-标准快递", 0.0, 0);
            }

            // 生成随机报价（89.0 ~ 109.0元之间，比A便宜但慢）
            double price = 89.0 + ThreadLocalRandom.current().nextDouble(20.0);

            log("供应商B 返回报价：¥" + String.format("%.2f", price));
            return new Quote("供应商B-标准快递", price, 3);
        } catch (Exception e) {
            // 捕获可能的异常（如 InterruptedException）
            log("供应商B 询价过程被中断或出错: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 模拟向供应商C询价（最慢的供应商，约800ms响应）
     *
     * @return 包含供应商名称、价格和交货周期的报价对象
     */
    private static Quote querySupplierC() throws InterruptedException {
        // 模拟网络延迟：供应商C响应最慢（800ms左右）
        Thread.sleep(800);

        try {
            // 检查当前线程是否被中断（很可能已经被取消了）
            if (Thread.currentThread().isInterrupted()) {
                log("供应商C 检测到中断信号（大概率已被取消），停止处理");
                return new Quote("供应商C-经济物流", 0.0, 0);
            }

            // 生成随机报价（79.0 ~ 99.0元之间，最便宜但也最慢）
            double price = 79.0 + ThreadLocalRandom.current().nextDouble(20.0);

            log("供应商C 返回报价：¥" + String.format("%.2f", price));
            return new Quote("供应商C-经济物流", price, 7);
        } catch (Exception e) {
            log("供应商C 询价过程被中断或出错: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 报价数据类（使用 static record 定义）
     * 封装供应商询价的完整信息
     *
     * @param supplier     供应商名称/服务类型
     * @param price        报价金额（元）
     * @param deliveryDays 承诺交货天数
     */
    private static record Quote(String supplier, double price, int deliveryDays) {
    }

    /**
     * 统一日志输出方法
     * 格式：[时间] [线程名] 消息内容
     * 便于观察并发执行顺序和各任务的执行情况
     */
    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
