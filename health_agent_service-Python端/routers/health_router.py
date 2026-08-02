# routers/health_router.py
from fastapi import APIRouter
from langchain_core.messages import HumanMessage
from schemas.health_schema import HealthPlanRequest
from schemas.chat_schema import ChatRequest
from agent.graph import run_health_agent

# 定义专属路由组
router = APIRouter(prefix="/api", tags=["运动健康中台"])


@router.post("/health-plan", summary="表单模式 - 生成专属运动健康计划")
async def generate_health_plan(request_data: HealthPlanRequest):
    """
    接收前端表单提交的用户身体数据，触发 LangGraph 状态机生成计划。
    适用场景：用户通过表单一次性提交所有数据。
    """
    print("====== 成功接收并校验前端表单数据 ======")
    print(f"身高: {request_data.height} cm")
    print(f"体重: {request_data.weight} kg")
    print(f"年龄: {request_data.age} 岁")
    print(f"动作类型: {request_data.movement_type}")
    print(f"极限重量: {request_data.current_1rm} kg")
    print(f"核心目标: {request_data.primary_goal}")
    if request_data.dormitory_rules:
        print(f"特殊限制: {request_data.dormitory_rules}")
    print("==============================================")

    # 构造合成消息（将表单数据翻译成自然语言）
    synthetic_msg_text = (
        f"你好，这是我从表单提交的数据：我今年{request_data.age}岁，"
        f"身高{request_data.height}cm，体重{request_data.weight}kg。"
        f"我测试过的动作是{request_data.movement_type}，极限重量是{request_data.current_1rm}kg。"
        f"我的核心目标是：{request_data.primary_goal}。"
        f"我已经准备好了所有数据，请直接为我生成专属健康计划。"
    )
    if request_data.dormitory_rules:
        synthetic_msg_text += f" 另外我的客观限制是：{request_data.dormitory_rules}。"

    # 将 Pydantic 模型转为 LangGraph State 所需的字典格式
    initial_state = {
        **request_data.model_dump(),
        "messages": [HumanMessage(content=synthetic_msg_text)],
        "assessment": None,
        "training_plan": None,
        "evaluation": None,
        "iteration_count": 0,
        "plan_generated": False,
        "is_ready": True,  # 🔑 表单模式：数据已齐全，直接触发后台生成
    }

    config = {"configurable": {"thread_id": f"user_form_{request_data.age}_{request_data.height}"}}

    result = await run_health_agent.ainvoke(initial_state, config=config)

    # 获取最后一条 AI 消息
    latest_reply = ""
    if result.get("messages"):
        latest_reply = result["messages"][-1].content

    # 构造精简的返回结构
    clean_data = {
        "assessment": result.get("assessment"),
        "training_plan": result.get("training_plan"),
        "latest_reply": latest_reply,
        "bmi_info": f"{result.get('height')}cm / {result.get('weight')}kg"
    }

    return {
        "code": 200,
        "message": "AI 大脑已完成分析",
        "data": clean_data
    }


@router.post("/chat", summary="对话模式 - 与健康智能体聊天")
async def chat_with_agent(request: ChatRequest):
    """
    支持多轮对话的健康智能体接口。
    前端发送用户消息和会话ID，后端维护对话状态，逐步收集数据并生成计划。

    流程：
    1. 售前模式：AI 与用户对话，收集身高/体重/年龄/目标等数据
    2. 数据齐全后自动触发后台生成计划
    3. 售后模式：AI 作为私人教练持续陪伴答疑
    """
    user_message = request.message
    session_id = request.session_id or "default_session"

    print(f"====== 收到会话 [{session_id}] 的新消息 ======")
    print(f"用户说: {user_message[:100]}...")
    print(f"历史消息数: {len(request.history or [])}")
    print("==============================================")

    # 构建消息列表：历史消息 + 当前用户消息
    messages = []
    if request.history:
        for msg in request.history:
            if msg.get("role") == "user":
                messages.append(HumanMessage(content=msg.get("content", "")))
            elif msg.get("role") == "assistant":
                from langchain_core.messages import AIMessage
                messages.append(AIMessage(content=msg.get("content", "")))

    # 添加当前用户消息
    messages.append(HumanMessage(content=user_message))

    # 构建初始状态（如果 history 中有之前保存的身体数据，也带过来）
    initial_state = {
        "messages": messages,
        "height": request.height or None,
        "weight": request.weight or None,
        "age": request.age or None,
        "movement_type": request.movement_type or "深蹲",
        "current_1rm": request.current_1rm or 0.0,
        "primary_goal": request.primary_goal or None,
        "dormitory_rules": request.dormitory_rules or None,
        "assessment": None,
        "training_plan": None,
        "evaluation": None,
        "iteration_count": 0,
        "plan_generated": False,
        "is_ready": False,
    }

    config = {"configurable": {"thread_id": session_id}}

    try:
        result = await run_health_agent.ainvoke(initial_state, config=config)

        # 提取返回结果
        latest_reply = ""
        if result.get("messages"):
            latest_reply = result["messages"][-1].content

        return {
            "code": 200,
            "message": "success",
            "data": {
                "reply": latest_reply,
                "assessment": result.get("assessment"),
                "training_plan": result.get("training_plan"),
                "height": result.get("height"),
                "weight": result.get("weight"),
                "age": result.get("age"),
                "primary_goal": result.get("primary_goal"),
                "plan_generated": result.get("plan_generated", False),
                "bmi_info": f"{result.get('height', '?')}cm / {result.get('weight', '?')}kg"
            }
        }
    except Exception as e:
        print(f"❌ 对话处理异常: {str(e)}")
        return {
            "code": 500,
            "message": f"AI引擎处理异常: {str(e)}",
            "data": None
        }


@router.get("/chat/history", summary="获取会话历史（预留接口）")
async def get_chat_history(session_id: str):
    """
    获取指定会话的聊天历史。
    当前版本为预留接口，后续可接入 Redis/数据库实现持久化。
    """
    return {
        "code": 200,
        "message": "此功能将在后续版本中实现",
        "data": {
            "session_id": session_id,
            "history": []
        }
    }
