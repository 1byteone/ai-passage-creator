-- Agent 助手会话与消息表：支撑纯对话 Agent 链路（T1）
-- 与 h2-schema.sql 同步维护（迁移集由 FlywayMigrationCompatibilityTest 验证）

-- agent_conversation：Agent 助手会话表
create table if not exists agent_conversation (
    id          bigint auto_increment primary key,
    user_id     bigint not null comment '登录用户',
    title       varchar(100) not null comment '会话标题（首条用户消息摘要）',
    create_time datetime not null default CURRENT_TIMESTAMP,
    update_time datetime default null,
    is_delete   tinyint default 0
);

-- agent_message：会话消息表
create table if not exists agent_message (
    id              bigint auto_increment primary key,
    conversation_id bigint not null,
    role            varchar(10)  not null comment 'user / assistant',
    kind            varchar(16)  not null default 'text' comment 'text / skill / error',
    content         text         not null,
    meta_json       text         default null comment 'skill 执行引用 / RAG 引用等元数据',
    create_time     datetime not null default CURRENT_TIMESTAMP,
    is_delete       tinyint default 0
);
create index if not exists idx_agent_msg_conv on agent_message(conversation_id, create_time);
