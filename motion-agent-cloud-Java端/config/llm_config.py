import os
from langchain.chat_models import init_chat_model
from dotenv import load_dotenv


load_dotenv()

llm = init_chat_model(
    model="qwen-plus",  # 模型选择qwen-plus
    model_provider="openai",
    base_url=os.getenv("DASHSCOPE_BASE_URL"),
    api_key=os.getenv("DASHSCOPE_API_KEY")
)
#llm = init_chat_model("deepseek-chat") Deepseek模型