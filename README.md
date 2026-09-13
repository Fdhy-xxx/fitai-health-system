# ⚡ FitAI — 分布式智能运动健康管理系统

> 基于 **LangGraph 多智能体协作** + **RocketMQ 异步消息** + **大语言模型** 的智能运动健康助手

<div align="center">

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![RocketMQ](https://img.shields.io/badge/RocketMQ-4.9.4-blue?logo=apacherocketmq)](https://rocketmq.apache.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.136-009688?logo=fastapi)](https://fastapi.tiangolo.com/)
[![LangGraph](https://img.shields.io/badge/LangGraph-1.2-purple)](https://langchain-ai.github.io/langgraph/)
[![Python](https://img.shields.io/badge/Python-3.13+-3776AB?logo=python)](https://www.python.org/)
[![Java](https://img.shields.io/badge/Java-17+-ED8B00?logo=openjdk)](https://openjdk.org/)
[![Docker](https://img.shields.io/badge/Docker-✓-2496ED?logo=docker)](https://www.docker.com/)

</div>

---

## 📖 项目简介

FitAI 是一款**分布式架构**的智能运动健康管理系统。它不只是简单的 AI 问答——更像一位真正的私人教练，先了解你的身体状况，再进行科学评估，接着定制专属训练计划，甚至会自我审查方案的安全性。

采用 **"前端 + Java 业务中台 + RocketMQ 消息中间件 + Python AI 中台 + 大语言模型"** 五层技术架构，实现异步解耦、削峰填谷的分布式 AI 推理流水线。

<div align="center">
  <img src="https://img.shields.io/badge/license-MIT-green" alt="license">
  <img src="https://img.shields.io/badge/PRs-welcome-brightgreen" alt="PRs Welcome">
</div>

---

## 🎯 核心功能

| 功能 | 说明 |
|------|------|
| 🤖 **AI 对话模式** | 像和朋友聊天一样与 AI 互动，AI 逐步收集身体数据，自动判断数据齐备后生成计划 |
| 📋 **快速评估模式** | 一次性填写身体数据表单，直接触发后台 AI 流水线生成完整计划 |
| 📊 **体能评估** | 基于身高、体重、年龄、力量水平，科学计算 BMI 和相对力量水平 |
| 🏋️ **训练计划生成** | 根据评估报告和目标，定制训练频次、动作编排、组数次数 |
| 🥗 **饮食建议** | 提供宏量营养素（碳水、蛋白、脂肪）配比建议和饮食时间规划 |
| 🔍 **自我审查** | 评估→生成→审查闭环，不合格方案自动重写（最多 3 次迭代） |
| 🛡️ **领域边界防护** | 严格限定运动健康领域，拒绝回答无关问题 |

---

## 🏗️ 技术架构

```
┌──────────────────────────────────────────────────────┐
│                   前端 (HTML5/CSS3/JS)                │
│          暗色运动科技风 · 响应式 · 零依赖             │
└──────────────────────┬───────────────────────────────┘
                       │ HTTP/REST API
                       ▼
┌──────────────────────────────────────────────────────┐
│            Java 业务中台 (Spring Boot 3.3)            │
│  Controller → MQ Producer → CorrelationManager       │
│  MQ Consumer → PythonAiClient (跨语言防腐层)          │
│  MyBatis-Plus → MySQL (数据持久化)                    │
└────────┬──────────────────────────────┬───────────────┘
         │ Request Topic                │ Reply Topic
         ▼                              ▲
┌──────────────────────────────────────────────────────┐
│            RocketMQ 消息中间件 (异步解耦)              │
│  Dashboard: http://127.0.0.1:8081 (可视化监控)       │
└──────────────────────┬───────────────────────────────┘
                       │ HTTP/REST API
                       ▼
┌──────────────────────────────────────────────────────┐
│           Python AI 中台 (FastAPI + LangGraph)        │
│  Chat → Analyzer → Generator → Evaluator → Output    │
└──────────────────────┬───────────────────────────────┘
                       │ API 调用
                       ▼
┌──────────────────────────────────────────────────────┐
│           大语言模型 (通义千问 / DeepSeek)            │
└──────────────────────────────────────────────────────┘
```

### LangGraph 智能体工作流

```
用户输入 → [Chat 客服前台]
                │
        ┌── 数据是否齐备? ──┐
        ▼ 否                ▼ 是
   [等待下一轮]      [Analyzer 数据分析师]
                           │
                    [Generator 计划生成器]
                           │
                    [Evaluator 审查专家]
                           │
              ┌── 是否通过? ──┐
              ▼ 否(≤3次)      ▼ 是/≥3次
         [打回重写]      [FinalOutput 最终输出]
                               │
                          返回给用户
```

---

## 📁 项目结构

```
分布式智能运动健康管理系统/
├── frontend-前端/                        # 🌟 前端页面
│   ├── index.html                        # 主页面（AI对话 + 快速评估 + 关于）
│   ├── css/style.css                     # 暗色运动科技风样式
│   └── js/
│       ├── api.js                        # API 通信模块
│       ├── chat.js                       # 聊天功能模块
│       └── app.js                        # 主应用逻辑
│
├── motion-agent-cloud-Java端/            # ☕ Java 业务中台
│   └── src/main/java/com/fitai/health/
│       ├── client/PythonAiClient.java    # AI 中台调用防腐层
│       ├── config/
│       │   ├── CorsConfig.java
│       │   └── RocketMQConfig.java       # MQ Topic 配置
│       ├── controller/
│       │   └── HealthPlanController.java
│       ├── mq/                            # 🚀 RocketMQ 消息模块
│       │   ├── CorrelationManager.java   # 请求-回复关联管理
│       │   ├── dto/                      # 消息体 DTO
│       │   ├── producer/                 # 消息生产者
│       │   └── consumer/                 # 消息消费者
│       └── service/                      # 业务逻辑层
│
├── health_agent_service-Python端/        # 🐍 Python AI 中台
│   ├── main.py                           # FastAPI 入口
│   ├── agent/
│   │   ├── graph.py                      # LangGraph 状态图
│   │   ├── nodes.py                      # 五个智能体节点
│   │   ├── prompts.py                    # 专家提示词
│   │   └── state.py                      # 状态数据结构
│   ├── routers/health_router.py          # API 路由
│   └── schemas/                          # 数据模型
│
├── rocketmq-env/                         # 🐳 RocketMQ Docker 部署
│   ├── docker-compose.yml                # 容器编排
│   └── broker.conf                       # Broker 配置
│
├── CHANGELOG.md
└── 项目说明文档.md                        # 详细中文文档
```

---

## 🚀 快速开始

### 环境要求

| 组件 | 版本要求 |
|------|---------|
| Python | ≥ 3.13 |
| Java JDK | 17+ |
| MySQL | 8.0+ |
| Maven | 3.6+ |
| Docker + Compose | 最新稳定版 |

### 1. 克隆项目

```bash
git clone https://github.com/YOUR_USERNAME/fitai-health-system.git
cd fitai-health-system
```

### 2. 启动 RocketMQ

```bash
cd rocketmq-env

# ⚠️ 修改 broker.conf 中的 brokerIP1 为本机 IP 地址
# Windows: 运行 ipconfig 查看 IPv4 地址
# Linux/Mac: 运行 ifconfig 或 ip addr

docker-compose up -d

# 验证: 访问 http://127.0.0.1:8081 (RocketMQ Dashboard)
```

### 3. 启动 Python AI 中台

```bash
cd health_agent_service-Python端

# 安装依赖
pip install -r requirements.txt

# 配置环境变量 (.env)
# DASHSCOPE_API_KEY=你的阿里百炼API密钥

# 启动服务
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### 4. 启动 Java 业务中台

```bash
cd motion-agent-cloud-Java端

# 确保 MySQL 已启动，创建数据库:
# CREATE DATABASE db_motion_agent DEFAULT CHARSET utf8mb4;

mvn clean package -DskipTests
java -jar target/health-0.0.1-SNAPSHOT.jar
```

### 5. 启动前端

```bash
# 用浏览器直接打开 frontend-前端/index.html
# 或使用 HTTP 服务器:
cd frontend-前端
python -m http.server 3000
# 访问 http://localhost:3000
```

### 推荐启动顺序

```
MySQL → RocketMQ → Python AI → Java 业务中台 → 前端
```

验证：前端右上角显示 🟢 **AI引擎在线**，Dashboard 显示四个 Topic 消息统计。

---

## 🛠️ 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **前端** | HTML5 + CSS3 + JavaScript | 原生技术，暗色运动科技风 |
| **Java 中台** | Spring Boot 3.3 + MyBatis-Plus | 业务逻辑 + 消息路由 |
| **消息中间件** | Apache RocketMQ 4.9.4 | 异步解耦 + 削峰填谷 |
| **Python AI** | FastAPI + LangGraph + LangChain | 多智能体协作 |
| **大模型** | 通义千问 qwen3-max / DeepSeek | 运动科学级分析 |
| **数据库** | MySQL 8.0 | 用户健康数据存储 |
| **容器化** | Docker + Docker Compose | RocketMQ 部署 |

---

## 🌟 项目亮点

1. **LangGraph 多智能体协作** — 客服→分析→生成→审查→输出，模拟真人教练团队
2. **自我审查迭代优化** — 不合格方案自动重写，最多 3 轮，确保方案质量
3. **RocketMQ 异步架构** — 企业级消息队列实现 Java ↔ Python 异步解耦与削峰
4. **自实现 Request-Reply 消息模式** — 请求 Topic + 回复 Topic + requestId 关联，CorrelationManager + CompletableFuture 完成异步应答
5. **分布式双服务架构** — Java 业务中台 + Python AI 中台，通过 RocketMQ 解耦、按职责拆分（说明:这是双服务拆分，不是微服务，未引入服务注册发现/治理组件）
6. **跨语言数据适配层（防腐层）** — 统一字段命名转换、时间格式与空值语义处理，Python 的数据结构不渗透进业务代码
7. **表单接口异步化 + 任务状态机** — 「落库受理 → 投递 MQ → 立即返回任务 ID」，表单接口受理响应由 40s+ 降至毫秒级；任务状态用带状态条件的更新流转，重复消息自动忽略
8. **消息可靠性与消费端幂等** — 生产端同步发送并校验投递结果；消费端按异常可重试性分流，重试耗尽进死信队列；requestId + Redis SETNX 防重复调用大模型
9. **暗色运动科技风 UI** — 玻璃拟态卡片 + 霓虹渐变 + 微交互动画
10. **双模式交互** — 对话模式（新手友好）+ 表单模式（高效提交）

---

## 📸 界面预览

- **AI 对话模式** — 自然聊天体验，AI 逐步收集数据，自动生成计划
- **快速评估模式** — 表单一次性填写，触发 LangGraph 流水线
- **侧边栏健康档案** — 实时显示已收集的身体数据 + BMI 指数
- **RocketMQ Dashboard** — 可视化监控消息生产消费情况

---

## 📝 License

MIT License — 详见 [LICENSE](LICENSE) 文件

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

<p align="center">
  <b>Made with 💪 by FitAI Team</b><br>
  <sub>让 AI 成为每个人的专属运动健康顾问</sub>
</p>
