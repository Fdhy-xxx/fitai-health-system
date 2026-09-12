package com.fitai.health.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fitai.health.common.constant.HealthPlanStatus;
import com.fitai.health.common.exception.BusinessException;
import com.fitai.health.config.RocketMQConfig;
import com.fitai.health.mapper.UserHealthMapper;
import com.fitai.health.model.dto.ChatRequestDTO;
import com.fitai.health.model.dto.HealthPlanRequestDTO;
import com.fitai.health.model.entity.UserHealthDO;
import com.fitai.health.mq.CorrelationManager;
import com.fitai.health.mq.dto.MqHealthPlanReply;
import com.fitai.health.mq.dto.MqHealthPlanRequest;
import com.fitai.health.mq.producer.HealthPlanMqProducer;
import com.fitai.health.service.HealthPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 健康计划服务实现
 *
 * <p>两种模式采用了不同的交互策略，原因是它们的用户预期不同：</p>
 * <ul>
 *   <li><b>表单模式（异步）</b>：AI 生成一份完整方案需要数十秒，属于长耗时任务。
 *       采用「受理即返回 + 前端轮询状态」的方式，接口只做校验、落库、投递消息，
 *       不占用业务线程等待，同时获得削峰与故障隔离能力。</li>
 *   <li><b>对话模式（同步）</b>：交互式场景下用户期望即时看到回复，改成轮询体验更差，
 *       因此保留「MQ 请求 + 等待应答」，由消息队列承担削峰职责。</li>
 * </ul>
 *
 * @author 郑新跃
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthPlanServiceImpl implements HealthPlanService {

    private final UserHealthMapper userHealthMapper;
    private final HealthPlanMqProducer healthPlanMqProducer;
    private final CorrelationManager correlationManager;
    private final RocketMQConfig rocketMQConfig;

    /**
     * 受理健康计划生成请求（表单模式，异步）
     *
     * <p>执行顺序：参数组装 → 落库（状态=待处理）→ 投递 MQ → 立即返回受理凭证。</p>
     * <p>注意：本方法不再使用 @Transactional。原实现在事务中做远程调用并阻塞等待数十秒，
     * 属于超长事务，数据库连接会被长期占用；现在事务范围只剩一次 insert，
     * 而单条 insert 本身即具备原子性，无需显式事务。</p>
     */
    @Override
    public Map<String, Object> createUserHealthPlan(HealthPlanRequestDTO requestDTO) {
        log.info("开始受理健康计划请求，目标: {}", requestDTO.getPrimaryGoal());

        // 1. 生成 requestId：既作为消息关联键，也作为消费端幂等键
        String requestId = UUID.randomUUID().toString();

        UserHealthDO userHealthDO = new UserHealthDO();
        BeanUtil.copyProperties(requestDTO, userHealthDO);
        userHealthDO.setRequestId(requestId);
        userHealthDO.setStatus(HealthPlanStatus.PENDING);

        // 2. 先落库，拿到自增主键作为前端轮询凭证
        userHealthMapper.insert(userHealthDO);
        Long recordId = userHealthDO.getId();
        log.info("任务已落库 [recordId={}, requestId={}]", recordId, requestId);

        // 3. 组装并投递 MQ 消息
        MqHealthPlanRequest mqRequest = MqHealthPlanRequest.builder()
                .age(requestDTO.getAge())
                .height(requestDTO.getHeight())
                .weight(requestDTO.getWeight())
                .movementType(requestDTO.getMovementType())
                .current1rm(requestDTO.getCurrent1rm())
                .primaryGoal(requestDTO.getPrimaryGoal())
                .dormitoryRules(requestDTO.getDormitoryRules())
                .build();

        try {
            healthPlanMqProducer.sendHealthPlanRequest(mqRequest, requestId);
        } catch (Exception e) {
            // 投递失败：必须把任务置为失败，否则该记录会永远停留在"待处理"，
            // 前端将无限轮询且用户永远看不到结果
            log.error("消息投递失败，标记任务为失败 [recordId={}]", recordId, e);
            userHealthMapper.update(null, new LambdaUpdateWrapper<UserHealthDO>()
                    .eq(UserHealthDO::getId, recordId)
                    .set(UserHealthDO::getStatus, HealthPlanStatus.FAILED)
                    .set(UserHealthDO::getErrorMsg, "消息投递失败：" + e.getMessage()));
            throw new BusinessException("任务提交失败，请稍后重试");
        }

        // 4. 立即返回受理凭证，不等待 AI 结果
        Map<String, Object> result = new HashMap<>(4);
        result.put("recordId", recordId);
        result.put("requestId", requestId);
        result.put("status", HealthPlanStatus.PENDING);
        log.info("健康计划请求已受理 [recordId={}, requestId={}]，接口无需等待 AI 结果", recordId, requestId);
        return result;
    }

    @Override
    public UserHealthDO queryById(Long id) {
        return userHealthMapper.selectById(id);
    }

    /**
     * 对话模式：保留同步等待（等待时长由 mq.chat-timeout-ms 控制）
     */
    @Override
    public Map<String, Object> chatWithAgent(ChatRequestDTO requestDTO) {
        log.info("开始处理聊天请求，会话: {}, 消息: {}", requestDTO.getSessionId(),
                requestDTO.getMessage().substring(0, Math.min(50, requestDTO.getMessage().length())));

        MqHealthPlanRequest mqRequest = MqHealthPlanRequest.builder()
                .message(requestDTO.getMessage())
                .sessionId(requestDTO.getSessionId())
                .history(requestDTO.getHistory())
                .height(requestDTO.getHeight())
                .weight(requestDTO.getWeight())
                .age(requestDTO.getAge())
                .movementType(requestDTO.getMovementType())
                .current1rm(requestDTO.getCurrent1rm())
                .primaryGoal(requestDTO.getPrimaryGoal())
                .dormitoryRules(requestDTO.getDormitoryRules())
                .build();

        // 1. 发送 MQ 并获取 requestId
        String requestId = healthPlanMqProducer.sendChatRequest(mqRequest);

        // 2. 注册 Future 并等待回复（对话场景用户期望即时回复，故保留等待）
        long chatTimeoutMs = rocketMQConfig.getChatTimeoutMs();
        CompletableFuture<MqHealthPlanReply> future = correlationManager.register(requestId, chatTimeoutMs);

        MqHealthPlanReply reply;
        try {
            reply = future.get(chatTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("MQ 聊天请求超时 [requestId={}]", requestId);
            correlationManager.cancel(requestId);
            Map<String, Object> errorResult = new HashMap<>(2);
            errorResult.put("reply", "抱歉，AI引擎响应超时，请稍后再试。");
            return errorResult;
        } catch (InterruptedException | ExecutionException e) {
            log.error("MQ 聊天请求异常 [requestId={}]: {}", requestId, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            correlationManager.cancel(requestId);
            Map<String, Object> errorResult = new HashMap<>(2);
            errorResult.put("reply", "抱歉，AI引擎处理异常，请稍后再试。");
            return errorResult;
        }

        Map<String, Object> result = new HashMap<>(12);
        if (reply != null && reply.isSuccess()) {
            result.put("reply", reply.getReply());
            result.put("assessment", reply.getAssessment());
            result.put("training_plan", reply.getTrainingPlan());
            result.put("height", reply.getHeight());
            result.put("weight", reply.getWeight());
            result.put("age", reply.getAge());
            result.put("primary_goal", reply.getPrimaryGoal());
            result.put("plan_generated", reply.getPlanGenerated());
            result.put("bmi_info", reply.getBmiInfo());
            log.info("聊天响应成功，plan_generated: {}", reply.getPlanGenerated());
        } else {
            log.warn("AI聊天响应为空或失败");
            result.put("reply", reply != null ? reply.getErrorMessage() : "抱歉，我暂时无法回复，请稍后再试。");
        }
        return result;
    }
}
