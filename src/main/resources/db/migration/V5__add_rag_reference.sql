-- RAG 参考溯源表：记录创作各阶段注入的检索参考（供详情页溯源与质量审计）
-- 与 h2-schema.sql 同步维护（迁移集由 FlywayMigrationCompatibilityTest 验证）
create table if not exists rag_reference (
    id bigint auto_increment primary key,
    task_id varchar(64) not null comment '文章任务ID',
    stage varchar(20) not null comment '创作阶段：title/outline/content',
    ref_id varchar(128) not null comment '参考源ID（文章taskId / 文档source）',
    ref_type varchar(20) null comment '参考类型：article/document/skill',
    ref_title varchar(256) null comment '参考标题（展示用）',
    score double null comment '相关度分数（重排后）',
    create_time datetime default CURRENT_TIMESTAMP not null,
    index idx_rag_ref_task (task_id)
);
