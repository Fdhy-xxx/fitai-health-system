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

/**
 * 健康计划接口
 *
 * <p>表单模式与对话模式采用了不同的交互模型：</p>
 * <ul>
 *   <li>表单模式：POST 受理（毫秒级返回 recordId）→ GET 轮询任务状态（异步）</li>
 *   <li>对话模式：POST 同步返回回复（交互式场景需要即时反馈）</li>
 * </ul>
 *
 * @author 郑新跃
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthPlanController {

    private final HealthPlanService healthPlanService;

    /**
     * 表单模式 - 受理健康计划生成请求（异步）
     *
     * <p>立即返回受理凭证 {@code { recordId, requestId, status }}，
     * 前端凭 recordId 轮询 {@code GET /api/health-plan/{id}} 获取进度与结果。</p>
     */
    @PostMapping("/health-plan")
    public Result<Map<String, Object>> generateHealthPlan(@Validated @RequestBody HealthPlanRequestDTO requestDTO) {
        log.info("收到表单模式健康计划请求，目标: {}", requestDTO.getPrimaryGoal());
        Map<String, Object> accepted = healthPlanService.createUserHealthPlan(requestDTO);
        return Result.success("任务已受理，正在生成中", accepted);
    }

    /**
     * 查询任务状态与结果（前端轮询使用）
     *
     * @param id 任务记录 id
     * @return 任务记录，status 取值见 HealthPlanStatus：1待处理 2处理中 3已完成 4失败
     */
    @GetMapping("/health-plan/{id}")
    public Result<UserHealthDO> queryHealthPlan(@PathVariable("id") Long id) {
        UserHealthDO record = healthPlanService.queryById(id);
        if (record == null) {
            return Result.error(404, "任务不存在");
        }
        return Result.success(record);
    }

    /**
     * 对话模式 - 与健康智能体聊天（同步返回）
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
