package com.fitai.health.mq;

import com.fitai.health.mq.dto.MqHealthPlanReply;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 请求-回复关联管理器
 * 维护 correlationId → CompletableFuture 的映射关系
 *
 * 核心流程:
 * 1. Controller 调用 register() 注册一个 Future
 * 2. ReplyConsumer 收到 MQ 回复后调用 complete() 完成 Future
 * 3. Controller await() 被唤醒，返回结果给前端
 */
@Slf4j
@Component
public class CorrelationManager {

    /** 存储 correlationId → CompletableFuture 的映射，线程安全 */
    private final Map<String, CompletableFuture<MqHealthPlanReply>> pendingFutures = new ConcurrentHashMap<>();

    /**
     * 注册一个待处理的请求
     * @param requestId 请求唯一标识
     * @param timeoutMs 超时时间 (ms)
     * @return CompletableFuture 用于等待异步结果
     */
    public CompletableFuture<MqHealthPlanReply> register(String requestId, long timeoutMs) {
        CompletableFuture<MqHealthPlanReply> future = new CompletableFuture<>();

        // 设置超时：超时后返回一个错误结果而不是抛异常
        future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("⏰ MQ 请求超时 [requestId={}]: {}", requestId, ex.getMessage());
                    return MqHealthPlanReply.builder()
                            .requestId(requestId)
                            .success(false)
                            .errorMessage("AI引擎处理超时，请稍后重试")
                            .build();
                });

        pendingFutures.put(requestId, future);
        log.debug("📝 注册 MQ 待处理请求 [requestId={}], 当前待处理数: {}", requestId, pendingFutures.size());
        return future;
    }

    /**
     * 完成一个请求（由 ReplyConsumer 调用）
     * @param requestId 请求唯一标识
     * @param reply 回复内容
     */
    public void complete(String requestId, MqHealthPlanReply reply) {
        CompletableFuture<MqHealthPlanReply> future = pendingFutures.remove(requestId);
        if (future != null) {
            future.complete(reply);
            log.info("✅ MQ 请求已完成 [requestId={}], 剩余待处理数: {}", requestId, pendingFutures.size());
        } else {
            log.warn("⚠️ 收到未知 requestId 的回复 [requestId={}], 可能已超时被清理", requestId);
        }
    }

    /**
     * 清理超时的请求
     * @param requestId 请求唯一标识
     */
    public void cancel(String requestId) {
        CompletableFuture<MqHealthPlanReply> future = pendingFutures.remove(requestId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            log.info("🛑 MQ 请求已取消 [requestId={}]", requestId);
        }
    }

    /**
     * 获取当前待处理的请求数
     */
    public int getPendingCount() {
        return pendingFutures.size();
    }
}
