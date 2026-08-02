package com.fitai.health.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatRequestDTO {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息长度不能超过2000字")
    private String message;

    private String sessionId;

    private List<Map<String, Object>> history;

    // 已收集的用户数据
    private Double height;
    private Double weight;
    private Integer age;
    private String movementType;
    private Double current1rm;
    private String primaryGoal;
    private String dormitoryRules;
}
