-- ============================================================
-- 2026-09-13 · 健康计划任务状态改造
-- 目标：为「接口异步化 + 任务状态流转」提供数据结构支撑
--
-- 说明：本项目原先没有版本化的 SQL 脚本，tb_user_health 是手工创建的，
--       因此本文件不会自动执行，需要手动在 Navicat / 命令行中执行一次。
--
-- 执行前务必备份：
--   CREATE TABLE tb_user_health_bak_20260913 AS SELECT * FROM tb_user_health;
-- ============================================================

ALTER TABLE tb_user_health
    -- 幂等键 + 回复消息关联键（消费端按它定位任务记录）
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT 'MQ 请求唯一标识：幂等键 + 回复消息关联键' AFTER id,
    -- 任务状态：异步化改造后接口不再等待 AI 结果，状态由消息链路推进
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '任务状态：1待处理 2处理中 3已完成 4失败' AFTER training_plan,
    -- 失败原因，便于前端展示与问题排查
    ADD COLUMN error_msg VARCHAR(500) NULL COMMENT '失败原因' AFTER status,
    -- 唯一索引：Redis 幂等失效时的最终兜底（重复插入直接失败）
    ADD UNIQUE KEY uk_request_id (request_id),
    -- 支撑「扫描超时未完成任务」这类查询
    ADD KEY idx_status_create_time (status, create_time);


-- ============================================================
-- 回退脚本（需要时手动执行）
-- ============================================================
-- ALTER TABLE tb_user_health
--     DROP INDEX uk_request_id,
--     DROP INDEX idx_status_create_time,
--     DROP COLUMN request_id,
--     DROP COLUMN status,
--     DROP COLUMN error_msg;
