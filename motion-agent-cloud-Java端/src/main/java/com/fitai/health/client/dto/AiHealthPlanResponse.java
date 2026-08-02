package com.fitai.health.client.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AiHealthPlanResponse {

    // 对应 Python 返回的 {"code": 200}
    private Integer code;

    // 对应 Python 返回的 {"message": "AI 大脑已完成分析"}
    private String message;

    // 对应 Python 返回的核心内容 {"data": {...}}
    private PythonData data;

    @Data
    public static class PythonData {
        // 精准对接 Python State 里的字段
        private String assessment;
        private String trainingPlan;

        // 内部流转状态位
        private Boolean planGenerated;
        private Boolean isReady;
        private Integer iterationCount;

        // 基础身体指标回传
        private Double height;
        private Double weight;
        private Integer age;
        private String primaryGoal;
    }
}