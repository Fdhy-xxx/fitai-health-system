package com.fitai.health.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@Slf4j
@Service
@RequiredArgsConstructor
public class HealthPlanServiceImpl implements HealthPlanService {

    private final UserHealthMapper userHealthMapper;
    private final HealthPlanMqProducer healthPlanMqProducer;
    private final CorrelationManager correlationManager;
    private final RocketMQConfig rocketMQConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserHealthDO createUserHealthPlan(HealthPlanRequestDTO requestDTO) {
        log.info("开始处理健康计划请求，目标: {}", requestDTO.getPrimaryGoal());

        UserHealthDO userHealthDO = new UserHealthDO();
        BeanUtil.copyProperties(requestDTO, userHealthDO);

        // MQ 异步调用：发送消息并等待回复
        MqHealthPlanRequest mqRequest = MqHealthPlanRequest.builder()
                .age(requestDTO.getAge())
                .height(requestDTO.getHeight())
                .weight(requestDTO.getWeight())
                .movementType(requestDTO.getMovementType())
                .current1rm(requestDTO.getCurrent1rm())
                .primaryGoal(requestDTO.getPrimaryGoal())
                .dormitoryRules(requestDTO.getDormitoryRules())
                .build();

        // 1. 发送 MQ 并获取 requestId
        String requestId = healthPlanMqProducer.sendHealthPlanRequest(mqRequest);

        // 2. 注册 Future 并等待回复（阻塞，但由 MQ 承担削峰）
        CompletableFuture<MqHealthPlanReply> future =
                correlationManager.register(requestId, rocketMQConfig.getRequestTimeoutMs());

        MqHealthPlanReply reply = null;
        try {
            reply = future.get(rocketMQConfig.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("MQ 请求超时 [requestId={}]", requestId);
            correlationManager.cancel(requestId);
            throw new BusinessException("AI引擎响应超时，请稍后重试");
        } catch (InterruptedException | ExecutionException e) {
            log.error("MQ 请求异常 [requestId={}]: {}", requestId, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            correlationManager.cancel(requestId);
            throw new BusinessException("AI引擎处理异常，请稍后重试");
        }

        if (reply == null || !reply.isSuccess()) {
            String errMsg = reply != null ? reply.getErrorMessage() : "未知错误";
            throw new BusinessException("AI引擎处理失败: " + errMsg);
        }

        // 3. 把 AI 回复填入 Entity
        if (reply.getAssessment() != null) {
            userHealthDO.setAssessment(reply.getAssessment());
            userHealthDO.setTrainingPlan(reply.getTrainingPlan());
            log.info("AI分析结果已接收，评估长度: {}, 计划长度: {}",
                    reply.getAssessment() != null ? reply.getAssessment().length() : 0,
                    reply.getTrainingPlan() != null ? reply.getTrainingPlan().length() : 0);
        } else {
            log.warn("AI响应为空，仅保存用户基础数据");
        }

        // 持久化到 MySQL
        userHealthMapper.insert(userHealthDO);
        log.info("用户健康记录已入库，ID: {}", userHealthDO.getId());
        return userHealthDO;
    }

    @Override
    public Map<String, Object> chatWithAgent(ChatRequestDTO requestDTO) {
        log.info("开始处理聊天请求，会话: {}, 消息: {}", requestDTO.getSessionId(),
                requestDTO.getMessage().substring(0, Math.min(50, requestDTO.getMessage().length())));

        // MQ 异步调用：发送消息并等待回复
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

        // 2. 注册 Future 并等待回复
        CompletableFuture<MqHealthPlanReply> future =
                correlationManager.register(requestId, rocketMQConfig.getRequestTimeoutMs());

        MqHealthPlanReply reply = null;
        try {
            reply = future.get(rocketMQConfig.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("MQ 聊天请求超时 [requestId={}]", requestId);
            correlationManager.cancel(requestId);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("reply", "抱歉，AI引擎响应超时，请稍后再试。");
            return errorResult;
        } catch (InterruptedException | ExecutionException e) {
            log.error("MQ 聊天请求异常 [requestId={}]: {}", requestId, e.getMessage());
            correlationManager.cancel(requestId);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("reply", "抱歉，AI引擎处理异常，请稍后再试。");
            return errorResult;
        }

        Map<String, Object> result = new HashMap<>();

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