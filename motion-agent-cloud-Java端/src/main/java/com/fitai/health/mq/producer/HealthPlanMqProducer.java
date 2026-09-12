package com.fitai.health.mq.producer;

import com.fitai.health.common.exception.BusinessException;
import com.fitai.health.config.RocketMQConfig;
import com.fitai.health.mq.dto.MqHealthPlanRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 健康计划消息生产者
 * 负责将业务请求封装为 MQ 消息发送到 Broker
 *
 * <p>可靠性说明：
 * 统一使用同步发送（syncSend）并校验 SendStatus，而不是异步 send。
 * 原因是 send 不关心发送结果，Broker 不可用或超时时消息会静默丢失，业务侧无感知；
 * 代价是每次发送多出几毫秒的往返等待，对"消息发不出去就等于业务失败"的场景是必须的取舍。</p>
 *
 * @author 郑新跃
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthPlanMqProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMQConfig rocketMQConfig;

    /**
     * 发送健康计划请求（表单模式）
     * requestId 由本方法生成，适用于调用方不需要提前落库的场景
     *
     * @param request 健康计划请求数据
     * @return 生成的 requestId
     */
    public String sendHealthPlanRequest(MqHealthPlanRequest request) {
        return sendHealthPlanRequest(request, UUID.randomUUID().toString());
    }

    /**
     * 发送健康计划请求（表单模式，指定 requestId）
     * <p>异步化改造后由 Service 先生成 requestId 并落库，再调用本方法，
     * 保证数据库中的任务记录与 MQ 消息可以通过 requestId 关联。</p>
     *
     * @param request   健康计划请求数据
     * @param requestId 业务唯一请求标识
     * @return requestId（原样返回，便于链式调用）
     */
    public String sendHealthPlanRequest(MqHealthPlanRequest request, String requestId) {
        request.setRequestId(requestId);
        request.setRequestType("health-plan");

        String topic = rocketMQConfig.getTopics().getHealthPlanRequest();
        syncSend(topic, request, requestId);
        return requestId;
    }

    /**
     * 发送聊天请求（对话模式）
     *
     * @param request 聊天请求数据
     * @return 生成的 requestId (correlationId)
     */
    public String sendChatRequest(MqHealthPlanRequest request) {
        String requestId = UUID.randomUUID().toString();
        request.setRequestId(requestId);
        request.setRequestType("chat");

        String topic = rocketMQConfig.getTopics().getChatRequest();
        syncSend(topic, request, requestId);
        return requestId;
    }

    /**
     * 同步发送并校验发送结果，失败直接抛出业务异常
     */
    private void syncSend(String topic, MqHealthPlanRequest payload, String requestId) {
        SendResult sendResult = rocketMQTemplate.syncSend(
                topic, MessageBuilder.withPayload(payload).build());

        if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
            log.error("❌ 消息发送失败 [requestId={}, topic={}, result={}]",
                    requestId, topic, sendResult);
            throw new BusinessException("消息投递失败，请稍后重试");
        }

        log.info("🚀 已发送消息 [requestId={}, msgId={}, type={}] → Topic: {}",
                requestId, sendResult.getMsgId(), payload.getRequestType(), topic);
    }
}
