-- 文章版本历史表
CREATE TABLE IF NOT EXISTS article_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_id VARCHAR(64) NOT NULL COMMENT '文章任务 ID',
    version_no INT NOT NULL COMMENT '版本号（从 1 递增）',
    round INT NOT NULL COMMENT '第几轮改写',
    content TEXT NOT NULL COMMENT '文章内容快照',
    change_summary TEXT COMMENT '本轮变更摘要',
    prompt_used TEXT COMMENT '使用的改写 Prompt',
    quality_score INT COMMENT '该版本质量评分',
    diff_base_version INT COMMENT 'diff 基准版本号',
    model_used VARCHAR(64) COMMENT '使用的模型',
    token_usage INT DEFAULT 0 COMMENT 'Token 消耗',
    duration_ms INT DEFAULT 0 COMMENT '耗时',
    created_by BIGINT COMMENT '操作用户 ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_ver (task_id, version_no),
    INDEX idx_task_round (task_id, round)
) COMMENT '文章版本历史表' COLLATE = utf8mb4_unicode_ci;
