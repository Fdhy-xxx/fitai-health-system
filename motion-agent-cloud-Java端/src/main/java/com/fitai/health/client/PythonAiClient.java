package com.fitai.health.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fitai.health.client.dto.AiHealthPlanResponse;
import com.fitai.health.client.dto.AiChatResponse;
import com.fitai.health.model.dto.HealthPlanRequestDTO;
import com.fitai.health.model.dto.ChatRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class PythonAiClient {

    // Jackson ObjectMapper 配置: 自动将蛇形命名转为驼峰命名
    // Python 返回 training_plan → Java 映射 trainingPlan
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // 动态读取 application.yml 中的 Python 接口路径
    @Value("${ai-service.url}")
    private String aiServiceUrl;

    @Value("${ai-service.chat-url:http://127.0.0.1:8000/api/chat}")
    private String aiChatUrl;

    /**
     * 调用 Python FastAPI 智能体中台 - 表单模式
     * @param requestDTO Java 侧接收到的标准身体与目标数据
     * @return 解析完毕的 AI 分析与训练计划响应体
     */
    public AiHealthPlanResponse requestHealthPlan(HealthPlanRequestDTO requestDTO) {
        log.info("==== [防腐层] 开始组装数据并发起 AI 算力中台调用 ====");

        // 1. 利用 Hutool 将 Java DTO 极速转化为序列化 JSON 字符串
        // ==== [防腐层核心功能] 将 Java 的驼峰命名翻译为 Python 的下划线命名 ====
        JSONObject pythonPayload = new JSONObject();
        pythonPayload.set("age", requestDTO.getAge());
        pythonPayload.set("height", requestDTO.getHeight());
        pythonPayload.set("weight", requestDTO.getWeight());

        // 重点翻译以下字段
        pythonPayload.set("movement_type", requestDTO.getMovementType());
        pythonPayload.set("current_1rm", requestDTO.getCurrent1rm());
        pythonPayload.set("primary_goal", requestDTO.getPrimaryGoal());
        pythonPayload.set("dormitory_rules", requestDTO.getDormitoryRules());

        String jsonPayload = pythonPayload.toString();
        log.info("翻译后发送给 Python 端的 Payload: {}", jsonPayload);

        try {
            // 2. 发起同步 HTTP POST 请求，等待 Python 端大模型分析
            log.info("正在向 AI 引擎发起网络请求，地址: {} ...", aiServiceUrl);
            // 设置 120 秒的超长等待时间
            String responseJson = HttpRequest.post(aiServiceUrl)
                    .body(jsonPayload)
                    .timeout(120000) // 核心: 给大模型两分钟的思考时间
                    .execute()
                    .body();

            log.info("==== [防腐层] AI 中台响应成功 ====");
            log.info("Python 原始响应 JSON: {}", responseJson);

            // 3. 使用 Jackson 反序列化 (自动蛇形→驼峰映射)
            return objectMapper.readValue(responseJson, AiHealthPlanResponse.class);

        } catch (Exception e) {
            log.error("调用 Python AI 服务发生致命网络异常或超时，URL: {}", aiServiceUrl, e);
            throw new RuntimeException("AI中台算力引擎暂时不可用，请稍后再试: " + e.getMessage());
        }
    }

    /**
     * 调用 Python FastAPI 智能体中台 - 对话模式
     * @param requestDTO 聊天请求数据
     * @return AI 聊天响应
     */
    public AiChatResponse requestChat(ChatRequestDTO requestDTO) {
        log.info("==== [防腐层-对话] 开始组装聊天数据并调用 AI 中台 ====");

        JSONObject pythonPayload = new JSONObject();
        pythonPayload.set("message", requestDTO.getMessage());
        pythonPayload.set("session_id", requestDTO.getSessionId());

        // 传递历史会话
        if (requestDTO.getHistory() != null) {
            pythonPayload.set("history", requestDTO.getHistory());
        }

        // 传递已收集的用户数据
        if (requestDTO.getHeight() != null) pythonPayload.set("height", requestDTO.getHeight());
        if (requestDTO.getWeight() != null) pythonPayload.set("weight", requestDTO.getWeight());
        if (requestDTO.getAge() != null) pythonPayload.set("age", requestDTO.getAge());
        if (requestDTO.getPrimaryGoal() != null) pythonPayload.set("primary_goal", requestDTO.getPrimaryGoal());
        if (requestDTO.getMovementType() != null) pythonPayload.set("movement_type", requestDTO.getMovementType());
        if (requestDTO.getCurrent1rm() != null) pythonPayload.set("current_1rm", requestDTO.getCurrent1rm());

        String jsonPayload = pythonPayload.toString();
        log.info("聊天请求 Payload: {}", jsonPayload);

        try {
            String responseJson = HttpRequest.post(aiChatUrl)
                    .body(jsonPayload)
                    .timeout(120000)
                    .execute()
                    .body();

            log.info("==== [防腐层-对话] AI 中台响应成功 ====");
            return objectMapper.readValue(responseJson, AiChatResponse.class);

        } catch (Exception e) {
            log.error("调用 Python AI 聊天服务发生异常，URL: {}", aiChatUrl, e);
            throw new RuntimeException("AI聊天服务暂时不可用: " + e.getMessage());
        }
    }
}
