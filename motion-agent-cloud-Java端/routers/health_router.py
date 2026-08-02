# routers/health_router.py
from fastapi import APIRouter
from schemas.health_schema import HealthPlanRequest
from agent.graph import run_health_agent

# 定义专属路由组（类似 Java 的 @RequestMapping("/api")）
router = APIRouter(prefix="/api", tags=["运动健康中台"])


@router.post("/health-plan", summary="生成专属运动健康计划")
async def generate_health_plan(request_data: HealthPlanRequest):
    """
        接收 Java 服务端发来的用户身体数据，触发 LangGraph 状态机生成计划。
        """
    # 打印测试：看看 Pydantic 的大门保安是否正常工作
    print("====== 成功接收并校验来自 Java 端的数据 ======")
    print(f"身高: {request_data.height} cm")
    print(f"体重: {request_data.weight} kg")
    print(f"动作类型: {request_data.movement_type}")
    print(f"极限重量: {request_data.current_1rm} kg")
    print(f"核心目标: {request_data.primary_goal}")
    if request_data.dormitory_rules:
        print(f"特殊限制: {request_data.dormitory_rules}")
    print("==============================================")

    # 将 Pydantic 模型转为 LangGraph State 所需的字典格式
    # 优雅的解包注入
    initial_state = {
        **request_data.model_dump(),
        "assessment": None,
        "training_plan": None,
        "evaluation": None,
        "iteration_count": 0,
    }

    # 定义配置，传入会话 ID（这里先写死用于测试，实际应从 token 中提取）
    config = {"configurable": {"thread_id": "user_0609"}}

    result = await run_health_agent.ainvoke(initial_state, config=config)
    return {
        "code": 200,
        "message": "AI 大脑已完成分析",
        "data": result
    }