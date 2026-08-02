package com.fitai.health.mq.consumer;

import com.fitai.health.mq.CorrelationManager;
import com.fitai.health.mq.dto.MqHealthPlanReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 回复消费者
 *
 * 监听回复 Topic，收到结果后通过 CorrelationManager 完成对应的 CompletableFuture，
 * 唤醒 Controller 层等待的 HTTP 请求，将结果返回给前端。
 *
 * 这是异步链路的"最后一公里"：MQ → 唤醒 HTTP 请求
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthPlanReplyConsumer {

    private final CorrelationManager correlationManager;

    /**
     * 消费健康计划回复
     */
    @Component
    @RocketMQMessageListener(
            topic = "${mq.topics.health-plan-reply}",
            consumerGroup = "${rocketmq.consumer.reply-group}",
            maxReconsumeTimes = 1
    )
    public class HealthPlanReplyListener implements RocketMQListener<MqHealthPlanReply> {

        @Override
        public void onMessage(MqHealthPlanReply reply) {
            log.info("📥 收到健康计划回复 [requestId={}, success={}]",
                    reply.getRequestId(), reply.isSuccess());
            correlationManager.complete(reply.getRequestId(), reply);
        }
    }

    /**
     * 消费聊天回复
     */
    @Component
    @RocketMQMessageListener(
            topic = "${mq.topics.chat-reply}",
            consumerGroup = "${rocketmq.consumer.chat-reply-group}",
            maxReconsumeTimes = 1
    )
    public class ChatReplyListener implements RocketMQListener<MqHealthPlanReply> {

        @Override
        public void onMessage(MqHealthPlanReply reply) {
            log.info("📥 收到聊天回复 [requestId={}, success={}]",
                    reply.getRequestId(), reply.isSuccess());
            correlationManager.complete(reply.getRequestId(), reply);
        }
    }
}
