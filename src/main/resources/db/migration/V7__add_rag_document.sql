-- RAG 知识库文档表：admin 上传的共享文档（按 source 幂等，重复上传覆盖）
-- 与 h2-schema.sql 同步维护
create table if not exists rag_document (
    id bigint auto_increment primary key,
    title varchar(200) null comment '文档标题（检索展示）',
    source varchar(512) not null comment '来源标识（幂等键，重复上传覆盖）',
    text longtext null comment '文档正文（索引前按 30000 截断 + 清洗）',
    user_id bigint null comment '上传人',
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null,
    constraint uq_rag_doc_source unique (source)
);
