-- 文章质量评分表
CREATE TABLE IF NOT EXISTS article_quality (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_id VARCHAR(64) NOT NULL COMMENT '文章任务 ID',
    article_content_snapshot TEXT COMMENT '评分时的文章内容快照',
    structure_score INT COMMENT '结构评分 (0-100)',
    logic_score INT COMMENT '逻辑评分 (0-100)',
    language_score INT COMMENT '语言评分 (0-100)',
    seo_score INT COMMENT 'SEO 评分 (0-100)',
    readability_score INT COMMENT '可读性评分 (0-100)',
    overall_score INT COMMENT '综合评分 (0-100)',
    suggestions JSON COMMENT '改进建议列表',
    strengths JSON COMMENT '亮点列表',
    model_used VARCHAR(64) COMMENT '使用的评分模型',
    token_usage INT DEFAULT 0 COMMENT 'Token 消耗',
    duration_ms INT DEFAULT 0 COMMENT '评分耗时',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_task_id (task_id),
    INDEX idx_overall_score (overall_score)
) COMMENT '文章质量评分表' COLLATE = utf8mb4_unicode_ci;
