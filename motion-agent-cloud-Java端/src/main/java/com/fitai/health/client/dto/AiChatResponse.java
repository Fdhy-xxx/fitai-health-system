package com.fitai.health.client.dto;

import lombok.Data;

@Data
public class AiChatResponse {

    private Integer code;
    private String message;
    private ChatData data;

    @Data
    public static class ChatData {
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
}
