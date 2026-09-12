package com.fitai.health.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 配置类
 * 定义消息中间件的核心常量与 Bean 配置
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "mq")
public class RocketMQConfig {

    /** 健康计划请求 Topic 名称 */
    private TopicNames topics = new TopicNames();

    /** MQ 请求超时 (ms) */
    private long requestTimeoutMs = 120000;

    /** 对话模式等待应答超时 (ms)：对话场景用户期望即时回复，超时不宜过长 */
    private long chatTimeoutMs = 60000;

    @Getter
    @Setter
    public static class TopicNames {
        private String healthPlanRequest = "health-plan-request-topic";
        private String healthPlanReply = "health-plan-reply-topic";
        private String chatRequest = "chat-request-topic";
        private String chatReply = "chat-reply-topic";
    }
}
