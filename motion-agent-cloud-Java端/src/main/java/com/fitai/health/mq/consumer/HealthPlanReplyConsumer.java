package com.fitai.health.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fitai.health.common.constant.HealthPlanStatus;
import com.fitai.health.mapper.UserHealthMapper;
import com.fitai.health.model.entity.UserHealthDO;
import com.fitai.health.mq.CorrelationManager;
import com.fitai.health.mq.dto.MqHealthPlanReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 回复消费者
 *
 * <p>两个 Listener 的处理方式不同，原因是两种模式的交互模型变了：</p>
 * <ul>
 *   <li><b>健康计划回复</b>：表单模式已异步化，接口不再等待结果，因此这里不再唤醒
 *       CompletableFuture（那样只会让 CorrelationManager 里的 Map 无限堆积造成内存泄漏），
 *       而是<b>按 requestId 把结果回写到任务记录</b>，由前端轮询获取。</li>
 *   <li><b>聊天回复</b>：对话模式保留同步等待，因此仍然通过 CorrelationManager 唤醒等待线程。</li>
 * </ul>
 *
 * <p>状态回写使用「带状态条件的更新」（WHERE status IN (1,2)），
 * 把流转合法性交给数据库的原子性判断：重复消息第二次影响行数为 0，自动被忽略。</p>
 *
 * @author 郑新跃
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthPlanReplyConsumer {

    private final UserHealthMapper userHealthMapper;
    private final CorrelationManager correlationManager;

    /**
     * 消费健康计划回复：回写任务状态与结果
     */
    @Component
    @RocketMQMessageListener(
            topic = "${mq.topics.health-plan-reply}",
            consumerGroup = "${rocketmq.consumer.reply-group}",
            maxReconsumeTimes = 3
    )
    public class HealthPlanReplyListener implements RocketMQListener<MqHealthPlanReply> {

        @Override
        public void onMessage(MqHealthPlanReply reply) {
            log.info("📥 收到健康计划回复 [requestId={}, success={}]",
                    reply.getRequestId(), reply.isSuccess());

            LambdaUpdateWrapper<UserHealthDO> update = new LambdaUpdateWrapper<UserHealthDO>()
                    .eq(UserHealthDO::getRequestId, reply.getRequestId())
                    // 只有「待处理 / 处理中」允许流转到终态；已完成的记录不会被重复消息改写
                    .in(UserHealthDO::getStatus, HealthPlanStatus.PENDING, HealthPlanStatus.PROCESSING)
                    .set(UserHealthDO::getStatus,
                            reply.isSuccess() ? HealthPlanStatus.SUCCESS : HealthPlanStatus.FAILED)
                    .set(UserHealthDO::getErrorMsg, reply.isSuccess() ? null : reply.getErrorMessage())
                    .set(UserHealthDO::getUpdateTime, LocalDateTime.now());

            if (reply.isSuccess()) {
                update.set(UserHealthDO::getAssessment, reply.getAssessment())
                        .set(UserHealthDO::getTrainingPlan, reply.getTrainingPlan());
            }

            int rows = userHealthMapper.update(null, update);
            if (rows == 0) {
                log.warn("⚠️ 回复未匹配到待处理任务（可能已处理或记录不存在）[requestId={}]",
                        reply.getRequestId());
            } else {
                log.info("✅ 任务状态已回写 [requestId={}, status={}]",
                        reply.getRequestId(),
                        reply.isSuccess() ? HealthPlanStatus.SUCCESS : HealthPlanStatus.FAILED);
            }
        }
    }

    /**
     * 消费聊天回复：唤醒等待中的业务线程
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
