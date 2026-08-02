package com.fitai.health.mq.consumer;

import com.fitai.health.client.PythonAiClient;
import com.fitai.health.client.dto.AiChatResponse;
import com.fitai.health.client.dto.AiHealthPlanResponse;
import com.fitai.health.config.RocketMQConfig;
import com.fitai.health.model.dto.ChatRequestDTO;
import com.fitai.health.model.dto.HealthPlanRequestDTO;
import com.fitai.health.mq.dto.MqHealthPlanReply;
import com.fitai.health.mq.dto.MqHealthPlanRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 请求消费者
 *
 * 监听请求 Topic，接收到消息后调用 Python AI 中台，
 * 然后将结果发布到回复 Topic。
 *
 * 这是整个异步架构的"转运中心"：MQ → HTTP(同步调用Python) → MQ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthPlanRequestConsumer {

    private final PythonAiClient pythonAiClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMQConfig rocketMQConfig;

    /**
     * 消费健康计划请求（表单模式）
     */
    @Component
    @RocketMQMessageListener(
            topic = "${mq.topics.health-plan-request}",
            consumerGroup = "${rocketmq.consumer.request-group}",
            maxReconsumeTimes = 3
    )
    public class HealthPlanRequestListener implements RocketMQListener<MqHealthPlanRequest> {

        @Override
        public void onMessage(MqHealthPlanRequest request) {
            log.info("📥 收到健康计划请求消息 [requestId={}]", request.getRequestId());

            MqHealthPlanReply reply = MqHealthPlanReply.builder()
                    .requestId(request.getRequestId())
                    .build();

            try {
                // 1. 将 MQ 消息转为 Java DTO
                HealthPlanRequestDTO dto = new HealthPlanRequestDTO();
                dto.setAge(request.getAge());
                dto.setHeight(request.getHeight());
                dto.setWeight(request.getWeight());
                dto.setMovementType(request.getMovementType());
                dto.setCurrent1rm(request.getCurrent1rm());
                dto.setPrimaryGoal(request.getPrimaryGoal());
                dto.setDormitoryRules(request.getDormitoryRules());

                // 2. 调用 Python AI 中台（同步 HTTP）
                AiHealthPlanResponse aiResponse = pythonAiClient.requestHealthPlan(dto);

                // 3. 组装回复
                if (aiResponse != null && aiResponse.getData() != null) {
                    reply.setSuccess(true);
                    reply.setAssessment(aiResponse.getData().getAssessment());
                    reply.setTrainingPlan(aiResponse.getData().getTrainingPlan());
                    reply.setPlanGenerated(aiResponse.getData().getPlanGenerated());
                    reply.setHeight(aiResponse.getData().getHeight());
                    reply.setWeight(aiResponse.getData().getWeight());
                    reply.setAge(aiResponse.getData().getAge());
                    reply.setPrimaryGoal(aiResponse.getData().getPrimaryGoal());
                } else {
                    reply.setSuccess(true);
                    log.warn("⚠️ AI 响应为空 [requestId={}]", request.getRequestId());
                }
            } catch (Exception e) {
                log.error("❌ 处理健康计划请求失败 [requestId={}]", request.getRequestId(), e);
                reply.setSuccess(false);
                reply.setErrorMessage("AI引擎处理异常: " + e.getMessage());
            }

            // 4. 发送回复到 Reply Topic
            String replyTopic = rocketMQConfig.getTopics().getHealthPlanReply();
            rocketMQTemplate.send(replyTopic, MessageBuilder.withPayload(reply).build());
            log.info("📤 已发送健康计划回复 [requestId={}, success={}] → Topic: {}",
                    reply.getRequestId(), reply.isSuccess(), replyTopic);
        }
    }

    /**
     * 消费聊天请求（对话模式）
     */
    @Component
    @RocketMQMessageListener(
            topic = "${mq.topics.chat-request}",
            consumerGroup = "${rocketmq.consumer.chat-request-group}",
            maxReconsumeTimes = 3
    )
    public class ChatRequestListener implements RocketMQListener<MqHealthPlanRequest> {

        @Override
        public void onMessage(MqHealthPlanRequest request) {
            log.info("📥 收到聊天请求消息 [requestId={}]", request.getRequestId());

            MqHealthPlanReply reply = MqHealthPlanReply.builder()
                    .requestId(request.getRequestId())
                    .build();

            try {
                // 1. 将 MQ 消息转为聊天 DTO
                ChatRequestDTO dto = new ChatRequestDTO();
                dto.setMessage(request.getMessage());
                dto.setSessionId(request.getSessionId());
                dto.setHistory(request.getHistory());
                dto.setHeight(request.getHeight());
                dto.setWeight(request.getWeight());
                dto.setAge(request.getAge());
                dto.setMovementType(request.getMovementType());
                dto.setCurrent1rm(request.getCurrent1rm());
                dto.setPrimaryGoal(request.getPrimaryGoal());
                dto.setDormitoryRules(request.getDormitoryRules());

                // 2. 调用 Python AI 中台
                AiChatResponse aiResponse = pythonAiClient.requestChat(dto);

                // 3. 组装回复
                if (aiResponse != null && aiResponse.getData() != null) {
                    AiChatResponse.ChatData data = aiResponse.getData();
                    reply.setSuccess(true);
                    reply.setReply(data.getReply());
                    reply.setAssessment(data.getAssessment());
                    reply.setTrainingPlan(data.getTrainingPlan());
                    reply.setHeight(data.getHeight());
                    reply.setWeight(data.getWeight());
                    reply.setAge(data.getAge());
                    reply.setPrimaryGoal(data.getPrimaryGoal());
                    reply.setPlanGenerated(data.getPlanGenerated());
                    reply.setBmiInfo(data.getBmiInfo());
                } else {
                    reply.setSuccess(true);
                    reply.setReply("抱歉，我暂时无法回复，请稍后再试。");
                }
            } catch (Exception e) {
                log.error("❌ 处理聊天请求失败 [requestId={}]", request.getRequestId(), e);
                reply.setSuccess(false);
                reply.setErrorMessage("AI引擎处理异常: " + e.getMessage());
            }

            // 4. 发送回复到 Reply Topic
            String replyTopic = rocketMQConfig.getTopics().getChatReply();
            rocketMQTemplate.send(replyTopic, MessageBuilder.withPayload(reply).build());
            log.info("📤 已发送聊天回复 [requestId={}, success={}] → Topic: {}",
                    reply.getRequestId(), reply.isSuccess(), replyTopic);
        }
    }
}
