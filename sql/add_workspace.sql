-- 协作空间表
CREATE TABLE IF NOT EXISTS workspace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(128) NOT NULL COMMENT '空间名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '空间描述',
    owner_id BIGINT NOT NULL COMMENT '创建者用户 ID',
    member_count INT DEFAULT 1 COMMENT '成员数量',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/ARCHIVED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_owner_id (owner_id)
) COMMENT '协作空间表' COLLATE = utf8mb4_unicode_ci;

-- 空间成员表
CREATE TABLE IF NOT EXISTS workspace_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workspace_id BIGINT NOT NULL COMMENT '空间 ID',
    user_id BIGINT NOT NULL COMMENT '成员用户 ID',
    role VARCHAR(20) NOT NULL DEFAULT 'member' COMMENT '角色：owner/admin/member/viewer',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY uk_workspace_user (workspace_id, user_id),
    INDEX idx_user_id (user_id)
) COMMENT '空间成员表' COLLATE = utf8mb4_unicode_ci;

-- 资源归属（文章/技能执行关联到空间）
CREATE TABLE IF NOT EXISTS workspace_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workspace_id BIGINT NOT NULL COMMENT '空间 ID',
    resource_type VARCHAR(30) NOT NULL COMMENT '资源类型：ARTICLE/SKILL_EXECUTION',
    resource_id VARCHAR(64) NOT NULL COMMENT '资源标识（taskId/executionId）',
    created_by BIGINT NOT NULL COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_workspace_resource (workspace_id, resource_type, resource_id),
    INDEX idx_resource (resource_type, resource_id)
) COMMENT '空间资源关联表' COLLATE = utf8mb4_unicode_ci;
