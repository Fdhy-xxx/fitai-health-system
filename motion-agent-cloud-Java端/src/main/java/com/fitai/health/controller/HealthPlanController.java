package com.fitai.health.controller;

import com.fitai.health.common.api.Result;
import com.fitai.health.model.dto.ChatRequestDTO;
import com.fitai.health.model.dto.HealthPlanRequestDTO;
import com.fitai.health.model.entity.UserHealthDO;
import com.fitai.health.service.HealthPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthPlanController {

    private final HealthPlanService healthPlanService;

    /**
     * 表单模式 - 生成健康计划
     */
    @PostMapping("/health-plan")
    public Result<UserHealthDO> generateHealthPlan(@Validated @RequestBody HealthPlanRequestDTO requestDTO) {
        log.info("收到表单模式健康计划请求，目标: {}", requestDTO.getPrimaryGoal());
        UserHealthDO resultData = healthPlanService.createUserHealthPlan(requestDTO);
        return Result.success("AI 大脑已完成分析并成功入库", resultData);
    }

    /**
     * 对话模式 - 与健康智能体聊天
     */
    @PostMapping("/chat")
    public Result<?> chatWithAgent(@Validated @RequestBody ChatRequestDTO requestDTO) {
        log.info("收到对话请求，会话: {}, 消息长度: {}", requestDTO.getSessionId(), requestDTO.getMessage().length());
        Map<String, Object> result = healthPlanService.chatWithAgent(requestDTO);
        return Result.success("success", result);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> healthCheck() {
        return Result.success("Java业务中台运行中");
    }
}