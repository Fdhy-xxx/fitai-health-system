package com.fitai.health.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tb_user_health")
public class UserHealthDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer age;
    private Double height;
    private Double weight;
    private String movementType;

    @TableField("current_1rm")
    private Double current1rm;
    private String primaryGoal;
    private String dormitoryRules;

    private String assessment;
    private String trainingPlan;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}