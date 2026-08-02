from typing import Literal

from langchain_core.messages import SystemMessage, HumanMessage, AIMessage
from langgraph.constants import END, START

from agent.state import HealthAgentState, EvaluationResult
from config.llm_config import llm

# 这里假设你把之前的专家提示词写在了 agent/prompts.py 里
# 如果没写，你可以直接把一大段提示词字符串赋给这个变量
from agent.prompts import ANALYZER_SYSTEM_PROMPT, GENERATOR_SYSTEM_PROMPT, EVALUATOR_SYSTEM_PROMPT, SCOPE_GUARD_PROMPT
from schemas.chat_schema import ChatContextExtraction


def chat_node(state: HealthAgentState):
    print(">>> 正在执行 Node 0: 前台客服接待...")

    # 给大模型套上结构化输出的紧箍咒
    chat_llm = llm.with_structured_output(ChatContextExtraction)

    # 核心修改点：读取锁的状态
    is_generated = state.get("plan_generated", False)

    if is_generated:
        # ==========================================
        # 🟢 【售后模式】纯闲聊，彻底去掉结构化输出！
        # ==========================================
        sys_prompt = f"""
            你现在是一位充满热情、像朋友一样的私人健身教练。
            系统已经为用户生成了专属的训练和饮食计划，你现在的核心任务是提供日常陪伴、答疑和心理支持。

            你的脑海中已经深深记住了该用户的档案：
            - 身高: {state.get('height', '未知')} cm
            - 体重: {state.get('weight', '未知')} kg
            - 核心目标: {state.get('primary_goal', '未知')}

            【行为绝对准则】：
            1. 必须用自然、口语化、有温度的人类语气聊天，多鼓励用户。
            2. 如果用户问“你还记得我的数据吗”，请直接、自豪地把上述身高体重和目标念给他听。
            3. 绝对不要再提“正在为您收集数据”、“呼叫专家排课”等售前话术。
            
            {SCOPE_GUARD_PROMPT}
            """

        messages = [SystemMessage(content=sys_prompt)] + state["messages"]

        # 🔑 关键改变：直接调用原生 llm，不需要 with_structured_output
        response = llm.invoke(messages)

        # 直接返回原生消息，不需要再提取 reply 和其他状态字段了
        return {"messages": [response]}
    else:
        # ==========================================
        # 🟡 【售前模式】收集数据，保留结构化输出紧箍咒
        # ==========================================
        chat_llm = llm.with_structured_output(ChatContextExtraction)

        sys_prompt = f"""
        你是一个专业的运动健康管理AI前台。
        你的任务是和用户自然对话，并暗中收集生成计划所需的三个核心数据：【身高】、【体重】和【核心目标】。
        如果用户了解过健身，你还可以询问他们【深蹲or卧推or硬拉】的极限重量(1RM)。
        如果用户没给全，你要温柔地引导他们补充。如果收集齐了，你要告诉他们“数据已收到，正在为您呼叫专家排课”。
        务必准确提取数值并填入对应的字段。
        1. 如果用户正在提供身高、体重、目标，请准确提取。
        2. 如果这是首次收集齐数据，你可以礼貌地回复“数据已记录，马上为您生成专属计划”。
        3. 如果用户是在计划生成后找你闲聊、索要打卡表、或者询问你的记忆（如“你知道我多高吗”），请像真实的私人教练一样自然地回答并满足他们的文本要求。
        4. 绝对不要在后续的闲聊中重复说“正在为您呼叫专家”这种机械的废话。
        5. 【主动引导风格偏好】：在收集数据的过程中或收集齐后，主动询问用户对计划风格的偏好。
        例如可以问：“你希望训练计划详细一些（包含所有动作组数次数营养建议），还是简洁一些（只给核心框架）？”
        或者“需要我给你制定一个非常详细的计划，还是重点突出的精简版？”
        根据用户回答，将风格偏好（如“详细”、“简洁”、“正常”）附加到 primary_goal 字段中，
        例如：“减脂（用户要求计划尽量简洁）”。
        6. 【极其重要】：如果用户在聊天中提出了对计划风格的要求（例如：“要简单的”、“不用太复杂”、“精简一点”），你必须将这些要求附加到 primary_goal 字段中！例如提取为：“减脂（用户要求计划尽量简单精简）”。
        
        {SCOPE_GUARD_PROMPT}
        """
        #【强制要求】请务必按照规定的 JSON 格式输出你的分析结果。 # <--- 加上这句护身符！
        messages = [SystemMessage(content=sys_prompt)] + state["messages"]

        # 这里返回的 response 是 ChatContextExtraction 对象
        response: ChatContextExtraction = chat_llm.invoke(messages)

        print(f"  [内部状态] 数据是否齐备: {response.is_ready}")
        print(f"  [内部状态] 提取到的体重: {response.weight}")
        print(f"  [内部状态] 提取到的身高: {response.height}")

        return {
            "messages": [AIMessage(content=response.reply)],
            # 继续保持数据收集逻辑
            "height": response.height if response.height is not None else state.get("height"),
            "weight": response.weight if response.weight is not None else state.get("weight"),
            "movement_type": response.movement_type if response.movement_type is not None else state.get(
                "movement_type"),
            "current_1rm": response.current_1rm if response.current_1rm is not None else state.get("current_1rm"),
            "primary_goal": response.primary_goal if response.primary_goal is not None else state.get("primary_goal"),
            # 🔑 把大模型的决策放进托盘
            "is_ready": response.is_ready
        }

    # # 注意：在对话模式下，我们直接把整个 messages 列表传给大模型，让它知道上下文
    # messages = [SystemMessage(content=sys_prompt)] + state["messages"]
    #
    # # 触发大模型，此时它返回的不再是纯文本，而是 ChatContextExtraction 对象！
    # response: ChatContextExtraction = chat_llm.invoke(messages)
    #
    # print(f"  [内部状态] 数据是否齐备: {response.is_ready}")
    # print(f"  [内部状态] 提取到的体重: {response.weight}")
    #
    # # 我们把提取到的数据和 AI 的回复全部塞回 State 托盘里
    # return {
    #     "messages": [AIMessage(content=response.reply)],
    #     # 极度安全的数据合并：如果大模型这次提取到了身高，就用新的；否则保留历史 state 里的身高！
    #     "height": response.height if response.height is not None else state.get("height"),
    #     "weight": response.weight if response.weight is not None else state.get("weight"),
    #     "movement_type": response.movement_type if response.movement_type is not None else state.get("movement_type"),
    #     "current_1rm": response.current_1rm if response.current_1rm is not None else state.get("current_1rm"),
    #     "primary_goal": response.primary_goal if response.primary_goal is not None else state.get("primary_goal"),
    # }


def route_after_chat(state: HealthAgentState) -> Literal["analyzer", "__end__"]:
    # 直接读取大模型的 is_ready 决定
    ai_is_ready = state.get("is_ready", False)# 检查锁的状态（默认是 False）
    is_generated = state.get("plan_generated", False)

    if ai_is_ready:
        if not is_generated:
            print(">>> 🟢 大模型确认无补充问题，激活后台专业流水线！")
            return "analyzer"
        else:
            print(">>> 🔵 计划已生成过，当前处于后续售后闲聊服务...")
            return END  # 纯聊天，停留在前台即可

    print(">>> 🟡 大模型觉得还需要再聊聊（或等待用户回答补充问题），停留在前台...")
    return END


def analyzer_node(state: HealthAgentState):
    # 1. 你的极佳亮点：提前算出 BMI
    bmi = state["weight"] / ((state["height"] / 100) ** 2)
    print(f">>> 后台计算得出 BMI: {bmi:.2f}")

    # 2. 把所有冷数据组装成“人话”，发给大模型
    user_context = f"""
    请评估以下用户：
    - 身高: {state['height']} cm
    - 体重: {state['weight']} kg
    - 实际计算 BMI: {bmi:.2f}
    - {state.get('movement_type', '深蹲')}极限重量(1RM): {state.get('current_1rm', 0)} kg
    - 核心目标: {state['primary_goal']}
    """

    # 3. 使用规范的消息列表：【系统人设】 + 【用户数据】
    messages = [
        SystemMessage(content=ANALYZER_SYSTEM_PROMPT),
        HumanMessage(content=user_context)
    ]

    # 4. 触发大模型思考
    print(">>> 正在调用大模型生成体测评估...")
    response = llm.invoke(messages)

    # 5. 更新 State 中的 assessment 字段
    return {"assessment": response.content}


from langchain_core.messages import SystemMessage, HumanMessage  # 删除了没用的 AIMessage
from agent.state import HealthAgentState
from config.llm_config import llm
from agent.prompts import GENERATOR_SYSTEM_PROMPT


def generator_node(state: HealthAgentState):
    print(">>> 正在调用大模型定制化计划生成...")

    # 1. 防御性编程：拿到上游结果，如果没有直接报错拦截
    upstream_assessment = state.get("assessment")
    if not upstream_assessment:
        raise ValueError("Node 2 致命错误：未能获取上游身体评估报告！")

    # 【新增核心逻辑】：读取上一轮的毒舌反馈
    eval_result = state.get("evaluation")
    feedback_context = ""
    # 如果有打分结果，并且是不合格，就把找茬意见提取出来
    if eval_result and eval_result.get("grade") == "fail":
        feedback_context = f"""
        【⚠️ 极高优先级修正指令】
        你上一版生成的计划已被审查专家打回！
        打回原因：{eval_result.get('feedback')}
        本次生成请务必解决上述致命问题，切勿重犯！
        """

    # 2. 组装最强上下文：评估报告 + 核心目标 + 外部限制全覆盖！
    # 将反馈拼接到大模型的上下文中
    generation_context = f"""
    请根据以下上游提供的专业评估报告，定制专属训练与饮食方案：
    【身体评估报告】
    {upstream_assessment}
    【用户的核心目标】
    {state['primary_goal']}
    【用户的客观限制条件】
    {state.get('dormitory_rules', '无特殊限制')}
    {feedback_context}  <-- 动态注入反馈
    """

    # 3. 严格使用 HumanMessage 传递上下文
    messages = [
        SystemMessage(content=GENERATOR_SYSTEM_PROMPT),
        HumanMessage(content=generation_context)
    ]

    # 4. 触发大模型思考
    response = llm.invoke(messages)

    # 5. 更新 State 中的 training_plan 字段
    return {"training_plan": response.content}


def evaluator_node(state: HealthAgentState):
    # 1. 绑定结构化输出（魔法就在这一行）
    evaluator_llm = llm.with_structured_output(EvaluationResult)
    # ... 组装上下文 messages 的代码 ...

    # 防御性编程：拿到上游结果，如果没有直接报错拦截
    upstream_training_plan = state.get("training_plan")
    if not upstream_training_plan:
        raise ValueError("Node 3 致命错误：未能获取上游训练与饮食方案！")

    # 组装上下文：评估报告 + 核心目标 + 外部限制全覆盖！
    evaluator_context = f"""
        请根据以下上游提供的训练与饮食方案，评估方案可行性：
        【训练与饮食方案】
        {upstream_training_plan}
        【用户的核心目标】
        {state['primary_goal']}
        【用户的客观限制条件】
        {state.get('dormitory_rules', '无特殊限制')}
        """

    messages = [
        SystemMessage(content=EVALUATOR_SYSTEM_PROMPT),
        HumanMessage(content=evaluator_context)
    ]

    # 2. 调用约束版的大模型
    response = evaluator_llm.invoke(messages)

    # 此时的 response 已经不再是普通的字符串了！
    # 它是一个完美的 EvaluationResult 类的实例，你可以直接 . 点出属性
    print(f"打分结果: {response.grade}")
    print(f"毒舌反馈: {response.feedback}")

    # 【新增核心逻辑】：每次审查完，让迭代次数 + 1
    current_iteration = state.get("iteration_count", 0) + 1
    print(f">>> 当前优化迭代轮数: {current_iteration}")

    # 同时返回更新后的 evaluation 和 iteration_count
    return {
        "evaluation": response.model_dump(),
        "iteration_count": current_iteration
    }


def route_after_eval(state: HealthAgentState) -> Literal["generator", "final_output"]:
    """评估后路由：pass→结束，fail→重新生成，超过3轮强制终止"""

    eval_result = state.get("evaluation", {})
    iteration_count = state.get("iteration_count", 0)

    # 1. 正常通过
    if eval_result.get("grade") == "pass":
        print("  ✓ 评估通过！准备将完美计划返回给前端。")
        return "final_output"

    # 2. 触发熔断机制（到达最大次数，强行停止内耗）
    if iteration_count >= 3:
        print(f"  ⚠ 触发系统熔断！已达到最大重试次数 (3次)，强制终止死循环，按当前最终版本输出。")
        return "final_output"

    # 3. 正常打回重造
    print(f"  ⚠ 评估未通过，打回 Node 2 重写。原因：{eval_result.get('feedback')}")
    return "generator"


from langchain_core.messages import AIMessage

def final_output_node(state: HealthAgentState):
    """
    信使节点：当计划最终通过审查后，把它包装成聊天消息发给用户
    """
    print(">>> 🟢 最终计划已生成，正在发送给用户...")

    final_reply = f"🎉 您的专属计划已生成，请查收！\n\n" \
                  f"📊【体能评估】\n{state['assessment']}\n\n" \
                  f"🏋️‍♂️【训练计划】\n{state['training_plan']}"

    # 注意：这里是存入 messages 数组，前端就能永远看到它了！
    return {
        "messages": [AIMessage(content=final_reply)],
        # 🔑 关键：把锁关上！告诉系统这份数据的计划已经做过了
        "plan_generated": True
    }