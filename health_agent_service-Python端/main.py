import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from agent.graph import run_health_agent
# 导入拆分出去的路由组件
from routers.health_router import router as health_router
from routers.chat_router import router as chat_router
# 【关键】在导入任何自定义业务模块之前，先加载环境变量！
# 这样后续无论哪个文件需要用到 os.getenv("DEEPSEEK_API_KEY") 都能拿到
load_dotenv()

# 导入校验模型
from schemas.health_schema import HealthPlanRequest

# 初始化 FastAPI 实例
app = FastAPI(
    title="分布式智能运动健康管理系统",
    description="Python AI 中台 - 提供 LangGraph 状态机流转服务",
    version="1.0.0"
)

# 🔧 新增：CORS 跨域中间件，允许前端直接调用
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应限制具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
async def root():
    return {"message": "健康智能体AI中台运行中 🏃"}


@app.get("/health")
async def health_check():
    """健康检查接口"""
    return {"status": "ok", "service": "health-agent-service"}


# 注册路由组件
app.include_router(health_router)
app.include_router(chat_router)

if __name__ == "__main__":
    # 使用 uv 启动时，通常可以直接用命令行 `uvicorn main:app --reload`
    # 这里为了开发方便，也保留代码启动方式
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)



