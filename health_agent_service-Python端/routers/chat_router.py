# routers/chat_router.py
"""
流式对话接口（SSE）。

为什么单独建一个路由文件：
对话链路原本是 前端 → Java 中台 → RocketMQ → Python → MQ 回复 → Java → 前端，
MQ 的投递粒度是"整条消息"，大模型逐 token 的输出在这条链路上天然无法透传。

本接口提供前端直连 Python 的 SSE 通道，按模式分流：
- 售后（计划已生成）：chat_node 售后分支本就是原生 llm 调用（无结构化输出约束），
  改用 llm.astream 即可逐 token 下发 —— 真·流式输出；
- 售前（收集中）：chat_node 使用 with_structured_output 约束输出为 JSON，
  token 流是 JSON 片段，直接透传给用户不可用，因此整体调用后一次性下发 final 事件，
  由前端以打字机动画渲染兜底。
"""
import json

from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from agent.graph import run_health_agent
from agent.prompts import build_after_sales_prompt
from config.llm_config import llm
from schemas.chat_schema import ChatRequest

router = APIRouter(prefix="/api", tags=["运动健康中台-流式对话"])


def _sse(event: str, data) -> str:
    """格式化为一条 SSE 事件（ensure_ascii=False 保证中文按原文传输）。"""
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def _build_messages(request: ChatRequest) -> list:
    """与 /chat 保持一致：历史消息 + 当前消息。"""
    messages = []
    if request.history:
        for msg in request.history:
            if msg.get("role") == "user":
                messages.append(HumanMessage(content=msg.get("content", "")))
            elif msg.get("role") == "assistant":
                messages.append(AIMessage(content=msg.get("content", "")))
    messages.append(HumanMessage(content=request.message))
    return messages


def _base_state(request: ChatRequest, messages: list) -> dict:
    """与 /chat 保持一致的初始状态构建。"""
    return {
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


def _chat_payload(state: dict, reply: str) -> dict:
    """与 /chat 返回结构保持一致的 data 载荷，前端两侧可复用同一套处理逻辑。"""
    return {
        "reply": reply,
        "assessment": state.get("assessment"),
        "training_plan": state.get("training_plan"),
        "height": state.get("height"),
        "weight": state.get("weight"),
        "age": state.get("age"),
        "primary_goal": state.get("primary_goal"),
        "plan_generated": state.get("plan_generated", False),
        "bmi_info": f"{state.get('height', '?')}cm / {state.get('weight', '?')}kg",
    }


@router.post("/chat/stream", summary="对话模式（SSE 流式）- 逐 token 下发回复")
async def chat_stream(request: ChatRequest):
    session_id = request.session_id or "default_session"
    config = {"configurable": {"thread_id": session_id}}
    messages = _build_messages(request)

    async def event_stream():
        try:
            # 读取检查点状态，判断售前/售后（与 chat_node 的判断依据一致）
            snapshot = await run_health_agent.aget_state(config)
            state_values = snapshot.values if snapshot is not None else {}
            is_after_sales = bool(state_values.get("plan_generated", False))

            yield _sse("meta", {"mode": "after" if is_after_sales else "pre"})

            if is_after_sales:
                # ========== 售后：原生 LLM 流式，逐 token 下发 ==========
                sys_prompt = build_after_sales_prompt(state_values)
                full_text = ""
                async for chunk in llm.astream(
                    [SystemMessage(content=sys_prompt)] + messages
                ):
                    token = chunk.content or ""
                    if token:
                        full_text += token
                        yield _sse("token", {"t": token})

                # 回写检查点：保持该 thread 的消息连续（messages 通道为追加语义），
                # 这样下一次 aget_state 判定与后续图调用都延续本次对话
                try:
                    await run_health_agent.aupdate_state(
                        config,
                        {"messages": [HumanMessage(content=request.message),
                                      AIMessage(content=full_text)]},
                    )
                except Exception as e:
                    print(f"⚠️ 流式链路检查点回写失败（不影响本次回复）: {e}")

                yield _sse("final", {"code": 200, "data": _chat_payload(state_values, full_text)})
            else:
                # ========== 售前：结构化输出无法逐 token 透传，整体调用 ==========
                result = await run_health_agent.ainvoke(
                    _base_state(request, messages), config=config
                )
                latest_reply = ""
                if result.get("messages"):
                    latest_reply = result["messages"][-1].content
                yield _sse("final", {"code": 200, "data": _chat_payload(result, latest_reply)})
        except Exception as e:
            print(f"❌ 流式对话处理异常: {e}")
            yield _sse("error", {"message": f"AI引擎处理异常: {str(e)}"})
        yield _sse("done", {"ok": True})

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            # 禁用 Nginx 等反代的响应缓冲，否则 token 会被攒成一大块
            "X-Accel-Buffering": "no",
        },
    )
