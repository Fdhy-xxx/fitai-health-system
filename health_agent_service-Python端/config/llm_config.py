import os
from langchain.chat_models import init_chat_model
from dotenv import load_dotenv


load_dotenv()

# 模型名支持通过环境变量覆盖，便于在不同模型间切换/做对照实验，无需改代码：
#   LLM_MODEL=qwen3.8-flash             （当前默认，额度可用）
#   LLM_MODEL=qwen3.7-flash-2026-07-15
#   LLM_MODEL=qwen3-max                 （原默认；需账户额度充足）
DEFAULT_MODEL = "qwen3.8-flash"
MODEL_NAME = os.getenv("LLM_MODEL", DEFAULT_MODEL)

# 超时与重试（必须显式设置）：
# 不设 timeout 时，网络抖动 / 代理中断会让请求无限挂起——批量评测时表现为
# 某个节点长时间无输出且不抛异常，with_retry 也救不了（因为压根没有异常可捕获）。
#   LLM_TIMEOUT 秒，默认 120（大模型长文本生成需留足时间）
#   LLM_MAX_RETRIES 次，默认 2（SDK 层自动重试瞬时故障）
# 思考模式（qwen3 系列特有）：
# 实测同一份计划生成任务（qwen3.8-flash）：
#   开启思考：175.8s，输出 8010 字（思考过程把输出撑得很长）
#   关闭思考： 33.2s，输出 3122 字
# 因此默认关闭——延迟降低约 5 倍，输出也更聚焦。
# 需要更强推理时可设 LLM_ENABLE_THINKING=true。
_ENABLE_THINKING = os.getenv("LLM_ENABLE_THINKING", "false").strip().lower() in ("1", "true", "yes")

llm = init_chat_model(
    model=MODEL_NAME,
    model_provider="openai",
    base_url=os.getenv("DASHSCOPE_BASE_URL"),
    api_key=os.getenv("DASHSCOPE_API_KEY"),
    timeout=float(os.getenv("LLM_TIMEOUT", "120")),
    max_retries=int(os.getenv("LLM_MAX_RETRIES", "2")),
    extra_body={"enable_thinking": _ENABLE_THINKING},
)
#llm = init_chat_model("deepseek-chat") Deepseek模型