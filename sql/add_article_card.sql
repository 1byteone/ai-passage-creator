-- 图文卡片生成子系统：article_card 表（MySQL 生产库 DDL）
-- 幂等：同 task_id + page_no 唯一键，重复生成采用先删后插
CREATE TABLE IF NOT EXISTS article_card (
    id bigint auto_increment primary key,
    task_id varchar(64) not null,
    page_no int not null,
    page_type varchar(16) default 'CONTENT' not null,
    style varchar(16) not null,
    image_url varchar(512) null,
    image_key varchar(256) null,
    width int default 1080,
    height int default 1920,
    bytes int default 0,
    status varchar(16) default 'PENDING' not null,
    compliance_report text null,
    error_message text null,
    render_ms int default 0,
    create_time datetime default CURRENT_TIMESTAMP,
    update_time datetime default CURRENT_TIMESTAMP,
    unique key uk_task_page (task_id, page_no)
);
