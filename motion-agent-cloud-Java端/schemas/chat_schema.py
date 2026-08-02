from pydantic import BaseModel, Field
from typing import Optional


class ChatContextExtraction(BaseModel):
    # 1. 陪聊字段：大模型想对用户说的话都在这里
    reply: str = Field(description="回复给用户的自然语言，语气要像专业的健身客服，必须是纯文本，绝对不能包含任何 JSON 标签或大括号")

    # 2. 核心数据提取字段：像雷达一样捕捉用户的数字
    height: Optional[float] = Field(None, description="从对话中提取到的身高(cm)")
    weight: Optional[float] = Field(None, description="从对话中提取到的体重(kg)")
    movement_type: Optional[str] = Field(None, description="运动动作名称，如深蹲、卧推")
    current_1rm: Optional[float] = Field(None, description="极限重量")
    primary_goal: Optional[str] = Field(None, description="用户的核心训练目标")

    # 3. 极其关键的状态位（
    is_ready: bool = Field(
        False,
        description="【极其重要】判断条件：当且仅当身高、体重、目标全都收集齐，【并且】你已经问完所有你想问的补充问题（比如确认计划风格、是否知道1RM等），准备好立刻让后台生成计划时，才设为 true。如果你在 reply 中向用户提出了任何疑问句，此处必须严格保持 false！"
    )