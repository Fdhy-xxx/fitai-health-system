package com.fitai.health.mq.consumer;

import com.fitai.health.client.PythonAiClient;
import com.fitai.health.client.dto.AiChatResponse;
import com.fitai.health.client.dto.AiHealthPlanResponse;
import com.fitai.health.common.exception.BusinessException;
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
 * 监听请求 Topic，接收到消息后调用 Python AI 中台，然后将结果发布到回复 Topic。
 * 这是整个异步架构的"转运中心"：MQ → HTTP(同步调用 Python) → MQ
 *
 * <p>异常处理原则（重要）：
 * RocketMQ 的重试机制建立在"消费者没有正常返回"之上 —— 只有方法抛异常，消息才不会被
 * ACK，才能进入重试（延迟级别递增）乃至死信队列。因此这里对异常做了分类：
 * <ul>
 *   <li>可重试异常（AI 服务不可用、超时、网络抖动）→ 抛出，交由 MQ 延迟重投</li>
 *   <li>不可重试异常（参数非法、业务校验失败）→ 捕获后返回失败结果并 ACK</li>
 * </ul>
 * 如果像原来那样把所有异常都 catch 住再发送回复，消费者等价于成功返回，
 * maxReconsumeTimes 与死信队列都永远不会生效。</p>
 *
 * @author 郑新跃
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthPlanRequestConsumer {

    private final PythonAiClient pythonAiClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMQConfig rocketMQConfig;

    /**
     * 发送回复消息（健康计划与聊天共用）
     */
    private void sendReply(MqHealthPlanReply reply, String topic) {
        rocketMQTemplate.send(topic, MessageBuilder.withPayload(reply).build());
        log.info("📤 已发送回复 [requestId={}, success={}] → Topic: {}",
                reply.getRequestId(), reply.isSuccess(), topic);
    }

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
                    // 响应为空同样属于失败（原实现这里标记成了 success=true，会让状态机误判为已完成）
                    reply.setSuccess(false);
                    reply.setErrorMessage("AI 引擎返回内容为空");
                    log.warn("⚠️ AI 响应为空 [requestId={}]", request.getRequestId());
                }
            } catch (BusinessException e) {
                // 不可重试：参数/业务校验类问题，直接返回失败并 ACK，不做无意义的重试
                log.warn("⚠️ 业务处理失败（不重试）[requestId={}]: {}",
                        request.getRequestId(), e.getMessage());
                reply.setSuccess(false);
                reply.setErrorMessage(e.getMessage());
            } catch (Exception e) {
                // 可重试：AI 服务不可用、调用超时、网络异常等
                // 【关键】必须抛出，不能吞掉：消费者正常返回等价于 ACK，消息不会重投
                log.error("❌ AI 引擎调用失败，交由 MQ 延迟重试 [requestId={}]",
                        request.getRequestId(), e);
                throw new RuntimeException("AI 引擎暂时不可用，触发消息重试", e);
            }

            // 4. 发送回复到 Reply Topic（仅正常处理完成或不可重试失败时到达这里）
            sendReply(reply, rocketMQConfig.getTopics().getHealthPlanReply());
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
                    reply.setSuccess(false);
                    reply.setErrorMessage("AI 引擎返回内容为空");
                    log.warn("⚠️ 聊天响应为空 [requestId={}]", request.getRequestId());
                }
            } catch (BusinessException e) {
                log.warn("⚠️ 聊天请求业务失败（不重试）[requestId={}]: {}",
                        request.getRequestId(), e.getMessage());
                reply.setSuccess(false);
                reply.setErrorMessage(e.getMessage());
            } catch (Exception e) {
                log.error("❌ 聊天请求调用 AI 失败，交由 MQ 延迟重试 [requestId={}]",
                        request.getRequestId(), e);
                throw new RuntimeException("AI 引擎暂时不可用，触发消息重试", e);
            }

            // 4. 发送回复到 Reply Topic
            sendReply(reply, rocketMQConfig.getTopics().getChatReply());
        }
    }
}
