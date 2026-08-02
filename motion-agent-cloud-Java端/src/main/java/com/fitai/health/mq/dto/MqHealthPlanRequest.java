package com.fitai.health.mq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * MQ 健康计划请求消息体
 * 携带 correlationId 用于异步请求-回复关联
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqHealthPlanRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求唯一标识 (correlationId)，用于关联请求与回复 */
    private String requestId;

    /** 请求类型: health-plan / chat */
    private String requestType;

    // ==================== 表单模式字段 ====================
    private Integer age;
    private Double height;
    private Double weight;
    private String movementType;
    private Double current1rm;
    private String primaryGoal;
    private String dormitoryRules;

    // ==================== 聊天模式字段 ====================
    private String message;
    private String sessionId;
    private List<Map<String, Object>> history;
}
