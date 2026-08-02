-- webhook_delivery 表 MySQL DDL
CREATE TABLE IF NOT EXISTS webhook_delivery (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    payload TEXT COMMENT '事件负载 JSON',
    target_url VARCHAR(512) COMMENT '目标 URL',
    signature VARCHAR(128) COMMENT 'HMAC 签名',
    attempt_count INT DEFAULT 0 COMMENT '重试次数',
    status VARCHAR(20) COMMENT '状态: PENDING/SUCCESS/FAILED/PERMANENTLY_FAILED',
    last_error VARCHAR(1024) COMMENT '最近一次错误',
    next_retry_at DATETIME COMMENT '下次重试时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME COMMENT '更新时间',
    INDEX idx_status_retry (status, next_retry_at)
) COMMENT 'Webhook 投递记录表' COLLATE = utf8mb4_unicode_ci;
