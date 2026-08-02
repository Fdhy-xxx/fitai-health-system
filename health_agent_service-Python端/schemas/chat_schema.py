from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any


class ChatContextExtraction(BaseModel):
    # 1. 陪聊字段：大模型想对用户说的话都在这里
    reply: str = Field(description="回复给用户的自然语言，语气要像专业的健身客服，必须是纯文本，绝对不能包含任何 JSON 标签或大括号")

    # 2. 核心数据提取字段：像雷达一样捕捉用户的数字
    height: Optional[float] = Field(None, description="从对话中提取到的身高(cm)")
    weight: Optional[float] = Field(None, description="从对话中提取到的体重(kg)")
    # ➕ 新增年龄提取字段
    age: Optional[int] = Field(None, description="从对话中提取到的年龄（岁），必须是正整数")
    movement_type: Optional[str] = Field(None, description="运动动作名称，如深蹲、卧推")
    current_1rm: Optional[float] = Field(None, description="极限重量")
    primary_goal: Optional[str] = Field(None, description="用户的核心训练目标")

    # 3. 极其关键的状态位
    is_ready: bool = Field(
        False,
        description="【极其重要】判断条件：当且仅当身高、体重、年龄、目标全都收集齐，【并且】你已经问完所有你想问的补充问题（比如确认计划风格、是否知道1RM等），准备好立刻让后台生成计划时，才设为 true。如果你在 reply 中向用户提出了任何疑问句，此处必须严格保持 false！"
    )


class ChatRequest(BaseModel):
    """前端聊天请求体"""
    message: str = Field(..., description="用户发送的消息内容", min_length=1, max_length=2000)
    session_id: Optional[str] = Field("default_session", description="会话ID，用于维护多轮对话上下文")
    history: Optional[List[Dict[str, Any]]] = Field(None, description="历史对话记录列表")
    # 以下字段用于在已收集部分数据后继续对话
    height: Optional[float] = Field(None, description="已收集的身高(cm)")
    weight: Optional[float] = Field(None, description="已收集的体重(kg)")
    age: Optional[int] = Field(None, description="已收集的年龄")
    movement_type: Optional[str] = Field(None, description="已收集的运动动作类型")
    current_1rm: Optional[float] = Field(None, description="已收集的极限重量")
    primary_goal: Optional[str] = Field(None, description="已收集的核心目标")
    dormitory_rules: Optional[str] = Field(None, description="已收集的客观限制条件")