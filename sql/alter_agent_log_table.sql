-- agent_log 表扩展 Skill 支持（表使用 camelCase 字段名）
ALTER TABLE `agent_log`
    ADD COLUMN `skill_execution_id` VARCHAR(64) DEFAULT NULL COMMENT '关联 Skill 执行 ID' AFTER `agentName`,
    ADD COLUMN `model_used` VARCHAR(64) DEFAULT NULL COMMENT '使用的模型' AFTER `prompt`,
    ADD COLUMN `token_usage` INT DEFAULT 0 COMMENT 'Token 消耗' AFTER `durationMs`,
    ADD INDEX `idx_skill_execution_id` (`skill_execution_id`);