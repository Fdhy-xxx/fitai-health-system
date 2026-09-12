package com.fitai.health.common.constant;

/**
 * 健康计划任务状态
 *
 * <p>异步化改造后，接口不再同步等待 AI 结果，任务的处理进度通过状态字段表达：</p>
 * <pre>
 * PENDING(1) ──消息投递成功/消费开始──> PROCESSING(2) ──AI 完成──> SUCCESS(3)
 *     │                                        │
 *     └──消息投递失败──> FAILED(4)              └──AI 异常/超时──> FAILED(4)
 * </pre>
 *
 * <p>状态流转的合法性由 SQL 的条件更新保证（例如
 * {@code UPDATE ... SET status = 3 WHERE id = ? AND status IN (1,2)}），
 * 用数据库的原子性替代应用层的并发判断，重复消息的影响行数为 0 即被自然忽略。</p>
 *
 * @author 郑新跃
 */
public interface HealthPlanStatus {

    /** 待处理：接口已受理并落库，消息已投递 */
    int PENDING = 1;

    /** 处理中：消费端已开始调用 AI 引擎 */
    int PROCESSING = 2;

    /** 已完成：AI 方案已回写 */
    int SUCCESS = 3;

    /** 失败：投递失败、AI 异常或重试耗尽 */
    int FAILED = 4;
}
