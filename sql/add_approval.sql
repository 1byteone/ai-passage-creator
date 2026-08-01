-- 文章审批记录表
CREATE TABLE IF NOT EXISTS approval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    article_task_id VARCHAR(64) NOT NULL COMMENT '文章任务 ID',
    version_no INT DEFAULT NULL COMMENT '审批的版本号',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED',
    submitted_by BIGINT COMMENT '提交人',
    reviewer_id BIGINT COMMENT '审批人',
    comment VARCHAR(1024) COMMENT '审批意见',
    submit_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    review_time DATETIME DEFAULT NULL COMMENT '审批时间',
    INDEX idx_article (article_task_id),
    INDEX idx_status (status)
) COMMENT '内容审批记录表' COLLATE = utf8mb4_unicode_ci;

-- 发布排期表
CREATE TABLE IF NOT EXISTS publish_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    article_task_id VARCHAR(64) NOT NULL COMMENT '文章任务 ID',
    publish_at DATETIME NOT NULL COMMENT '计划发布时间',
    status VARCHAR(20) DEFAULT 'SCHEDULED' COMMENT '状态：SCHEDULED/PUBLISHED/CANCELLED',
    published_at DATETIME DEFAULT NULL COMMENT '实际发布时间',
    created_by BIGINT COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_publish_at (publish_at),
    INDEX idx_status (status),
    INDEX idx_article (article_task_id)
) COMMENT '文章发布排期表' COLLATE = utf8mb4_unicode_ci;
