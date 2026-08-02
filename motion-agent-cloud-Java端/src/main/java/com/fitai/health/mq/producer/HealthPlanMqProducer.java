package com.fitai.health.mq.producer;

import com.fitai.health.config.RocketMQConfig;
import com.fitai.health.mq.dto.MqHealthPlanRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 健康计划消息生产者
 * 负责将业务请求封装为 MQ 消息发送到 Broker
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthPlanMqProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMQConfig rocketMQConfig;

    /**
     * 发送健康计划请求（表单模式）
     * @param request 健康计划请求数据
     * @return 生成的 requestId (correlationId)
     */
    public String sendHealthPlanRequest(MqHealthPlanRequest request) {
        String requestId = UUID.randomUUID().toString();
        request.setRequestId(requestId);
        request.setRequestType("health-plan");

        String topic = rocketMQConfig.getTopics().getHealthPlanRequest();
        rocketMQTemplate.send(topic, MessageBuilder.withPayload(request).build());
        log.info("🚀 已发送健康计划请求消息 [requestId={}] → Topic: {}", requestId, topic);
        return requestId;
    }

    /**
     * 发送聊天请求（对话模式）
     * @param request 聊天请求数据
     * @return 生成的 requestId (correlationId)
     */
    public String sendChatRequest(MqHealthPlanRequest request) {
        String requestId = UUID.randomUUID().toString();
        request.setRequestId(requestId);
        request.setRequestType("chat");

        String topic = rocketMQConfig.getTopics().getChatRequest();
        rocketMQTemplate.send(topic, MessageBuilder.withPayload(request).build());
        log.info("💬 已发送聊天请求消息 [requestId={}] → Topic: {}", requestId, topic);
        return requestId;
    }
}
