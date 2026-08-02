package com.fitai.health.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthPlanVO {

    private Long userId;

    private Integer age;

    private String gender;

    private Integer height;

    private Integer weight;

    private String activityLevel;

    private String healthGoal;

    private String planContent;

    private Integer calorieGoal;

    private String exercisePlan;

    private String dietPlan;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}