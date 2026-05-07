package com.example.multithread.structuredconcurrency;

import java.time.LocalTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * 知识点：StructuredTaskScope.ShutdownOnFailure（JDK 21 Preview，结构化并发）。
 *
 * 示例目标：
 * 1. 把"同一业务请求"的并发子任务放到一个作用域内管理。
 * 2. 用 ShutdownOnFailure 在任一子任务失败时快速取消其余任务。
 * 3. 在 join + throwIfFailed 后安全聚合结果。
 *
 * 核心概念：
 * - 结构化并发：子任务的生命周期被限定在父任务的代码块内，类似于结构化编程中
 *   "代码块结束时，其中创建的所有资源都会自动清理"的思想。
 * - ShutdownOnFailure：当任一子任务抛出异常时，自动调用 scope.shutdown() 取消其他
 *   所有还在运行的子任务，避免浪费资源。
 *
 * 注意：
 * 1. 该 API 在 JDK 21 中是 Preview 特性。
 * 2. 需要使用 --enable-preview 编译和运行。
 *
 * 编译命令（在项目根目录执行）：
 * $env:JAVA_HOME="D:\JDK\openjdk21\jdk-21"; mvn clean compile 2>&1
 *
 * 运行命令：
 * $env:JAVA_HOME="D:\JDK\openjdk21\jdk-21"; java --enable-preview -cp target/classes com.example.multithread.structuredconcurrency.StructuredTaskScopePreviewDemo
 */
public class StructuredTaskScopePreviewDemo {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        String userId = "U1001";

        // 使用 try-with-resources 确保 StructuredTaskScope 在作用域结束后自动关闭。
        // ShutdownOnFailure 策略：任一子任务失败，立即取消所有兄弟子任务。
        try (StructuredTaskScope.ShutdownOnFailure scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // 使用 fork() 提交并发子任务。fork() 会立即返回一个 Subtask 对象，
            // 子任务在后台线程（默认是虚拟线程）中异步执行。
            StructuredTaskScope.Subtask<UserProfile> profileTask = scope.fork(() -> queryUserProfile(userId));
            StructuredTaskScope.Subtask<Integer> scoreTask = scope.fork(() -> queryCreditScore(userId));

            // join() 阻塞等待所有子任务执行完毕（成功或失败）。
            // 如果某个子任务抛出异常且策略是 ShutdownOnFailure，
            // 则会触发 shutdown()，导致其他正在运行的子任务收到中断信号。
            log("开始等待所有子任务完成...");
            scope.join();

            // throwIfFailed() 检查是否有子任务失败。
            // 如果有任何一个子任务失败了，将异常包装后重新抛出。
            // 这确保了在获取结果之前，先处理可能的错误情况。
            scope.throwIfFailed();

            // 到这里说明所有子任务都成功了，安全地获取结果。
            UserProfile profile = profileTask.get();
            Integer score = scoreTask.get();

            log("聚合完成：name=" + profile.name + ", level=" + profile.level + ", creditScore=" + score);
        }
        // try-with-resources 结束时会自动调用 scope.close()，
        // 确保所有子任务线程都已结束，不会有资源泄漏。
    }

    /**
     * 模拟查询用户画像服务（耗时约450ms）
     */
    private static UserProfile queryUserProfile(String userId) throws InterruptedException {
        Thread.sleep(450);
        log("用户画像服务返回");
        return new UserProfile(userId, "张三", "VIP");
    }

    /**
     * 模拟查询信用评分服务（耗时约380ms）
     */
    private static Integer queryCreditScore(String userId) throws InterruptedException {
        Thread.sleep(380);
        log("信用评分服务返回");
        return 760;
    }

    /**
     * 用户画像数据类（使用 static record 定义）
     * 注意：使用 static 而非局部 record，以确保编译器兼容性
     */
    private static record UserProfile(String userId, String name, String level) {
    }

    /**
     * 统一日志输出方法，包含当前时间和线程名
     */
    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
