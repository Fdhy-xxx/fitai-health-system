package com.fitai.health.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;


@Data
public class HealthPlanRequestDTO {

    @NotNull(message = "年龄不能为空")
    @Min(value = 10, message = "年龄不能小于10岁")
    @Max(value = 120, message = "年龄不能大于120岁")
    private Integer age;

    @NotNull(message = "身高不能为空")
    @Min(value = 50, message = "身高输入不合法")
    private Double height;

    @NotNull(message = "体重不能为空")
    @Min(value = 20, message = "体重输入不合法")
    private Double weight;

    private String movementType = "深蹲";

    private Double current1rm = 0.0;

    @NotBlank(message = "核心目标不能为空")
    @Size(max = 500, message = "目标描述字数过长")
    private String primaryGoal;

    private String dormitoryRules;
}