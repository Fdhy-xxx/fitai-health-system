from typing import TypedDict, Optional, List, Literal, Annotated

from langchain_core.messages import AnyMessage
from langgraph.graph import add_messages
from pydantic import BaseModel, Field

# 引入官方内置的 MessagesState 基类
from langgraph.graph import MessagesState

class EvaluationResult(BaseModel):
    grade: Literal["pass", "fail"] = Field(
        description="最终打分，必须是 pass 或 fail"
    )
    feedback: str = Field(
        description="找茬反馈意见，如果是 fail，必须给出明确的修改方向"
    )


# 2. 核心：共享状态托盘（流转于各个 Node 之间）
class HealthAgentState(MessagesState):

    # ==========================================
    # 🌟 新增核心：对话上下文（赋予机器人聊天能力）
    # ==========================================
    # messages: Annotated[list[AnyMessage], add_messages]

    # ==========================================
    # 业务数据层（全部改为 Optional，聊天过程中逐步收集）
    # ==========================================
    height: Optional[float]  # 身高 (cm)
    weight: Optional[float]  # 体重 (kg)
    movement_type: Optional[str]  # 运动能力指标(拆分为动作和重量)
    current_1rm: Optional[float]  # 极限深蹲,卧推重量 (kg)
    primary_goal: Optional[str]  # 核心目标（例如：实战扣篮、减脂）
    dormitory_rules: Optional[str]  # 外部作息限制（例如：1:00 准时断电）

    # --- 流程演进层（被后续多个节点所需、重新获取成本高的数据） ---
    assessment: Optional[str]  # Analyzer 节点生成的身体代谢和力量评估报告
    training_plan: Optional[str]  # Generator 节点生成的初版/改版训练计划

    # --- 门控路由层（决策大脑） ---
    evaluation: Optional[EvaluationResult]  # Evaluator 节点写入的审查结果
    iteration_count: int  # 记录当前优化迭代了几轮，防止死循环

    # 🌟 新增：流程控制锁
    plan_generated: bool

    # 新增这个字段，用来接收大模型的意愿
    is_ready: bool
