package com.fitai.health.mq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * MQ 健康计划回复消息体
 * 携带与请求相同的 correlationId 用于匹配
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqHealthPlanReply implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 与请求对应的 correlationId */
    private String requestId;

    /** 是否成功 */
    private boolean success;

    /** 失败时的错误信息 */
    private String errorMessage;

    // ==================== AI 返回数据 ====================
    private String reply;
    private String assessment;
    private String trainingPlan;
    private Double height;
    private Double weight;
    private Integer age;
    private String primaryGoal;
    private Boolean planGenerated;
    private String bmiInfo;
}
