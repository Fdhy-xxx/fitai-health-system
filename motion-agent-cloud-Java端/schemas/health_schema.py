from pydantic import BaseModel, Field
from typing import Optional


class HealthPlanRequest(BaseModel):
    """
    接收 Java 业务侧发来的用户健康与目标数据
    """
    # 基础身体数据
    height: float = Field(
        ...,
        gt=10.0, lt=350.0,
        description="身高 (cm)，必须介于 10 到 350 之间",
        json_schema_extra={"example": 180.0}
    )
    weight: float = Field(
        ...,
        gt=0.0, lt=1000.0,
        description="体重 (kg)，必须介于 0 到 1000 之间",
        json_schema_extra={"example": 75.0}
    )

    # 运动能力指标(拆分为动作和重量)
    movement_type: str = Field(
        "深蹲",  # 默认值
        max_length=50,
        description="测试1RM的动作名称，如：深蹲、卧推、硬拉",
        json_schema_extra={"example": "卧推"}
    )
    # 运动能力指标(可选，如无经验可不填)
    current_1rm: float = Field(
        0.0,
        ge=0.0, lt=2000.0,
        description="该动作的极限重量 1RM (kg)，没有经验传 0",
        json_schema_extra={"example": 80.0}
    )

    # 主观诉求
    primary_goal: str = Field(
        ...,
        min_length=2, max_length=500,
        description="核心目标，如：实战扣篮、康复减脂等",
        json_schema_extra={"example": "提升垂直弹跳能力，目标完成实战扣篮"}
    )

    # 客观限制条件（可选）
    dormitory_rules: Optional[str] = Field(
        None,
        max_length=500,
        description="外部作息或场地限制条件",
        json_schema_extra={"example": "每周健身三天"}
    )

    model_config = {
        "json_schema_extra": {
            "description": "健康管理系统核心分析接口请求体"
        }
    }