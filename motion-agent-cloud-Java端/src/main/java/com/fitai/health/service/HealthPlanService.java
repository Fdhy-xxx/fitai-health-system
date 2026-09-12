package com.fitai.health.service;

import com.fitai.health.model.dto.ChatRequestDTO;
import com.fitai.health.model.dto.HealthPlanRequestDTO;
import com.fitai.health.model.entity.UserHealthDO;

import java.util.Map;

/**
 * 健康计划服务
 *
 * @author 郑新跃
 */
public interface HealthPlanService {

    /**
     * 受理健康计划生成请求（表单模式）
     *
     * <p>异步化改造后本方法<b>不再返回 AI 生成结果</b>：只完成参数校验、任务落库与消息投递，
     * 随即返回受理凭证（recordId / requestId / status），由前端凭 recordId 轮询任务状态。
     * 这样接口响应时间从"等待大模型生成完"的数十秒降为毫秒级。</p>
     *
     * @param requestDTO 前端提交的表单数据
     * @return 受理凭证，包含 recordId（轮询凭证）、requestId（幂等键）、status（初始状态）
     */
    Map<String, Object> createUserHealthPlan(HealthPlanRequestDTO requestDTO);

    /**
     * 查询任务状态与结果（前端轮询使用）
     *
     * @param id 任务记录 id
     * @return 任务记录；不存在时返回 null
     */
    UserHealthDO queryById(Long id);

    /**
     * 与健康智能体进行多轮对话
     *
     * <p>对话模式保留了同步等待：交互式场景下用户期望即时看到回复，
     * 改为异步轮询反而会损害体验，因此这里仍然通过 MQ 请求 + 等待应答的方式实现，
     * 由 MQ 承担削峰职责。</p>
     *
     * @param requestDTO 聊天请求数据
     * @return AI 回复及更新的用户数据
     */
    Map<String, Object> chatWithAgent(ChatRequestDTO requestDTO);
}
