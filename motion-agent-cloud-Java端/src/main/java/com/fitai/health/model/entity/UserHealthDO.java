package com.fitai.health.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tb_user_health")
public class UserHealthDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MQ 请求唯一标识：幂等键 + 回复消息关联键 */
    private String requestId;

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

    /** 任务状态：1待处理 2处理中 3已完成 4失败，取值见 HealthPlanStatus */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}