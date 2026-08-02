package com.fitai.health.service;

import com.fitai.health.model.dto.ChatRequestDTO;
import com.fitai.health.model.dto.HealthPlanRequestDTO;
import com.fitai.health.model.entity.UserHealthDO;

import java.util.Map;

public interface HealthPlanService {
    /**
     * 分析用户健康状态并生成训练计划（表单模式）
     * @param requestDTO 前端/Java业务侧传入的表单数据
     * @return 包含AI生成计划的完整领域对象
     */
    UserHealthDO createUserHealthPlan(HealthPlanRequestDTO requestDTO);

    /**
     * 与健康智能体进行多轮对话
     * @param requestDTO 聊天请求数据
     * @return AI回复及更新的用户数据
     */
    Map<String, Object> chatWithAgent(ChatRequestDTO requestDTO);
}