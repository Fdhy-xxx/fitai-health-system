from typing import TypedDict, Optional, List, Literal, Annotated

from langchain_core.messages import AnyMessage
from langgraph.graph import add_messages
from pydantic import BaseModel, Field

# 引入官方内置的 MessagesState 基类
from langgraph.graph import MessagesState

class EvaluationResult(BaseModel):
    """审查节点的结构化输出。

    改造要点：把"给一个笼统的判定"变成"先列约束清单、再逐条核查"。
    - checked_constraints：要求模型先把用户的硬性约束逐条抄出来，形成可追溯的核查项；
    - violations：要求对每一条约束给出"约束原文 → 计划中违背它的具体安排"，
      使 fail 判定有据可依，同时可用 len(violations) 量化审查力度。

    字段均为可选/带默认值，新增字段不影响既有路由逻辑（route_after_eval 只读 grade）。
    """

    grade: Literal["pass", "fail"] = Field(
        description="最终打分，必须是 pass 或 fail"
    )
    feedback: str = Field(
        description="找茬反馈意见，如果是 fail，必须给出明确的修改方向"
    )
    checked_constraints: List[str] = Field(
        default_factory=list,
        description=(
            "本轮实际核查过的『用户硬性约束』清单，逐条引用约束原文（伤病禁忌、"
            "作息/时间限制、可用器械、训练频率上限、目标方向）。用户未给出约束时为空数组。"
        ),
    )
    violations: List[str] = Field(
        default_factory=list,
        description=(
            "逐条列出发现的违背项，格式统一为『约束原文 → 计划中违背它的具体安排』。"
            "没有违背项时为空数组。"
        ),
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
    # ➕ 新增流程流转字段
    age: Optional[int] # 年龄 (岁)
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
