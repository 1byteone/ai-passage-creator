-- 创建 skill_execution 表（H2 兼容，不使用 MySQL 特有语法）
CREATE TABLE IF NOT EXISTS `skill_execution` (
    `id`                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    `skill_execution_id`  VARCHAR(64) NOT NULL UNIQUE,
    `skill_name`          VARCHAR(64) NOT NULL,
    `task_id`             VARCHAR(64) DEFAULT NULL,
    `user_id`             BIGINT NOT NULL,
    `status`              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    `phase`               VARCHAR(64) DEFAULT NULL,
    `input_data`          TEXT DEFAULT NULL,
    `output_data`         TEXT DEFAULT NULL,
    `result_url`          VARCHAR(512) DEFAULT NULL,
    `token_usage`         INT DEFAULT 0,
    `model_used`          VARCHAR(64) DEFAULT NULL,
    `duration_ms`         INT DEFAULT 0,
    `error_message`       TEXT DEFAULT NULL,
    `create_time`         DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`         DATETIME DEFAULT NULL,
    `is_delete`           TINYINT DEFAULT 0
);