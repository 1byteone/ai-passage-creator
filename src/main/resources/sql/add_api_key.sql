-- 平台级 API Key 认证：机器/外部客户端调用凭证
-- 幂等建表（MySQL 8.0+），列命名与 h2-schema.sql 保持一致（camelCase 物理列）
CREATE TABLE IF NOT EXISTS api_key (
    id bigint auto_increment primary key,
    userId bigint not null,
    name varchar(64) not null,
    apiKeyHash varchar(64) not null,
    apiKeyPrefix varchar(16) not null,
    lastUsedAt datetime null,
    expiresAt datetime null,
    createTime datetime default CURRENT_TIMESTAMP not null,
    updateTime datetime default CURRENT_TIMESTAMP not null,
    isDelete tinyint default 0 not null,
    constraint uq_apiKeyHash unique (apiKeyHash),
    index idx_apiKey_user (userId)
);
