from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import  START, END

from langgraph.graph import StateGraph

from agent.nodes import evaluator_node, route_after_eval, chat_node, final_output_node, route_after_chat
from agent.nodes import analyzer_node, generator_node
from agent.state import HealthAgentState

# memory = InMemorySaver()

run_health_agent = (
    StateGraph(HealthAgentState)
    .add_node("chat", chat_node)
    .add_node("analyzer", analyzer_node)
    .add_node("generator", generator_node)
    .add_node("evaluator", evaluator_node)
    .add_node("final_output", final_output_node)
    .add_edge(START, "chat")
    .add_conditional_edges("chat", route_after_chat, {
        END: END,             # 对应闲聊场景，直接结束当前图流并输出
        "analyzer": "analyzer"
    })
    .add_edge("analyzer", "generator")
    .add_edge("generator", "evaluator")
    .add_conditional_edges("evaluator", route_after_eval, {
        "generator": "generator",  # 不达标→回generator重写
        "final_output": "final_output"
    })
    .add_edge("final_output", END)
    # .compile(checkpointer=memory)
    .compile()
)