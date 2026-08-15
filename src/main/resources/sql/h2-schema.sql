create table if not exists `user` (
    id bigint auto_increment primary key,
    userAccount varchar(256) not null,
    userPassword varchar(512) not null,
    userName varchar(256) null,
    userAvatar varchar(1024) null,
    userProfile varchar(512) null,
    userRole varchar(256) default 'user' not null,
    quota int default 10 not null,
    vipTime datetime null,
    editTime datetime default CURRENT_TIMESTAMP not null,
    createTime datetime default CURRENT_TIMESTAMP not null,
    updateTime datetime default CURRENT_TIMESTAMP not null,
    isDelete tinyint default 0 not null,
    constraint uq_userAccount UNIQUE (userAccount)
);


-- 幂等插入：共享 H2 内存库跨测试上下文复用，重复执行需安全（MERGE 避免主键冲突）
MERGE INTO `user` (id, userAccount, userPassword, userName, userAvatar, userProfile, userRole) KEY(id) VALUES
(1, 'admin', '10670d38ec32fa8102be6a37f8cb52bf', '管理员', 'https://www.codefather.cn/logo.png', '系统管理员', 'admin'),
(2, 'user', '10670d38ec32fa8102be6a37f8cb52bf', '普通用户', 'https://www.codefather.cn/logo.png', '我是一个普通用户', 'user'),
(3, 'test', '10670d38ec32fa8102be6a37f8cb52bf', '测试账号', 'https://www.codefather.cn/logo.png', '这是一个测试账号', 'user');

create table if not exists article (
    id bigint auto_increment primary key,
    taskId varchar(64) not null,
    userId bigint not null,
    topic varchar(500) not null,
    userDescription text null,
    enabledImageMethods longtext null,
    style varchar(50) null,
    methodology varchar(64) default 'default' null,
    characterStyle varchar(64) default null comment '插画子风格（healing/cute/doodle/watercolor）',
    mainTitle varchar(200) null,
    subTitle varchar(300) null,
    titleOptions longtext null,
    outline longtext null,
    content text null,
    fullContent text null,
    coverImage varchar(512) null,
    images longtext null,
    status varchar(20) default 'PENDING' not null,
    phase varchar(50) null,
    errorMessage text null,
    createTime datetime default CURRENT_TIMESTAMP not null,
    completedTime datetime null,
    updateTime datetime default CURRENT_TIMESTAMP not null,
    isDelete tinyint default 0 not null,
    constraint uq_taskId UNIQUE (taskId)
);

create table if not exists skill_execution (
    id bigint auto_increment primary key,
    skill_execution_id varchar(64) not null unique,
    skill_name varchar(64) not null,
    task_id varchar(64) default null,
    user_id bigint not null,
    status varchar(30) not null default 'PENDING',
    phase varchar(64) default null,
    input_data text default null,
    output_data text default null,
    result_url varchar(512) default null,
    token_usage int default 0,
    model_used varchar(64) default null,
    duration_ms int default 0,
    error_message text default null,
    create_time datetime not null default CURRENT_TIMESTAMP,
    update_time datetime default null,
    is_delete tinyint default 0
);

create table if not exists agent_log (
    id bigint auto_increment primary key,
    taskId varchar(64) not null,
    agentName varchar(256) null,
    skill_execution_id varchar(64) default null,
    startTime datetime null,
    endTime datetime null,
    durationMs int null,
    tokenUsage int default 0,
    status varchar(20) null,
    errorMessage text null,
    prompt text null,
    modelUsed varchar(64) default null,
    inputData text null,
    outputData text null,
    createTime datetime default CURRENT_TIMESTAMP,
    updateTime datetime default CURRENT_TIMESTAMP,
    isDelete tinyint default 0
);

create table if not exists payment_record (
    id bigint auto_increment primary key,
    userId bigint not null,
    stripeSessionId varchar(256) null,
    stripePaymentIntentId varchar(256) null,
    amount int null,
    currency varchar(10) null,
    status varchar(20) null,
    productType varchar(50) null,
    description varchar(512) null,
    refundTime datetime null,
    refundReason varchar(512) null,
    createTime datetime default CURRENT_TIMESTAMP,
    updateTime datetime default CURRENT_TIMESTAMP,
    isDelete tinyint default 0
);

create table if not exists article_quality (
    id bigint auto_increment primary key,
    task_id varchar(64) not null,
    article_content_snapshot text,
    structure_score int,
    logic_score int,
    language_score int,
    seo_score int,
    readability_score int,
    overall_score int,
    suggestions text,
    strengths text,
    model_used varchar(64),
    token_usage int default 0,
    duration_ms int default 0,
    score_type varchar(16) default 'GENERIC' not null,
    user_id bigint null,
    viral_score decimal(5,2) null,
    viral_scores text null,
    title_strategy_hit varchar(32) null,
    methodology_used varchar(64) null,
    content_hash varchar(64) null,
    version_no int default 1 null,
    create_time datetime default CURRENT_TIMESTAMP
);

-- 唯一索引：同 taskId 同评测类型仅一行（配合 delete+insert 幂等 upsert，防并发双插）
create unique index if not exists idx_aq_task_score_type on article_quality(task_id, score_type);

create table if not exists workspace (
    id bigint auto_increment primary key,
    name varchar(128) not null,
    description varchar(512),
    owner_id bigint not null,
    member_count int default 1,
    status varchar(20) default 'ACTIVE',
    create_time datetime default CURRENT_TIMESTAMP,
    update_time datetime default CURRENT_TIMESTAMP
);

create table if not exists workspace_member (
    id bigint auto_increment primary key,
    workspace_id bigint not null,
    user_id bigint not null,
    role varchar(20) default 'member',
    joined_at datetime default CURRENT_TIMESTAMP,
    constraint uq_ws_user unique (workspace_id, user_id)
);

create table if not exists workspace_resource (
    id bigint auto_increment primary key,
    workspace_id bigint not null,
    resource_type varchar(30) not null,
    resource_id varchar(64) not null,
    created_by bigint not null,
    create_time datetime default CURRENT_TIMESTAMP,
    constraint uq_ws_res unique (workspace_id, resource_type, resource_id)
);

create table if not exists approval_record (
    id bigint auto_increment primary key,
    article_task_id varchar(64) not null,
    version_no int,
    status varchar(20) default 'PENDING',
    submitted_by bigint,
    reviewer_id bigint,
    comment varchar(1024),
    submit_time datetime default CURRENT_TIMESTAMP,
    review_time datetime
);

create table if not exists publish_schedule (
    id bigint auto_increment primary key,
    article_task_id varchar(64) not null,
    publish_at datetime not null,
    status varchar(20) default 'SCHEDULED',
    platform varchar(32) default 'wechat' null,
    content_title varchar(256) null,
    adapter_output text null,
    methodology_name varchar(64) default 'default' null,
    published_at datetime,
    created_by bigint,
    create_time datetime default CURRENT_TIMESTAMP
);

create table if not exists webhook_delivery (
    id bigint auto_increment primary key,
    event_type varchar(64) not null,
    payload text,
    target_url varchar(512),
    signature varchar(128),
    attempt_count int default 0,
    status varchar(20),
    last_error varchar(1024),
    next_retry_at datetime,
    create_time datetime default CURRENT_TIMESTAMP,
    update_time datetime
);

create table if not exists article_card (
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

create table if not exists api_key (
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

-- HITL 检查点持久化（与 V2__add_skill_checkpoint.sql 同步维护）
create table if not exists skill_checkpoint (
    id bigint auto_increment primary key,
    thread_id varchar(64) not null,
    checkpoint_id varchar(64) not null,
    node_id varchar(255) null,
    next_node_id varchar(255) null,
    state_data blob null,
    released tinyint default 0 not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null
);

-- RAG 参考溯源表（与 V5__add_rag_reference.sql 同步维护）
create table if not exists rag_reference (
    id bigint auto_increment primary key,
    task_id varchar(64) not null,
    stage varchar(20) not null,
    ref_id varchar(128) not null,
    ref_type varchar(20) null,
    ref_title varchar(256) null,
    score double null,
    create_time datetime default CURRENT_TIMESTAMP not null
);
create index if not exists idx_rag_ref_task on rag_reference(task_id);

-- Agent 会话与消息表（与 V6__create_agent_conversation.sql 同步维护）
create table if not exists agent_conversation (
    id bigint auto_increment primary key,
    user_id bigint not null,
    title varchar(100) not null,
    create_time datetime not null default CURRENT_TIMESTAMP,
    update_time datetime default null,
    is_delete tinyint default 0
);

create table if not exists agent_message (
    id bigint auto_increment primary key,
    conversation_id bigint not null,
    role varchar(10) not null,
    kind varchar(16) not null default 'text',
    content text not null,
    meta_json text default null,
    create_time datetime not null default CURRENT_TIMESTAMP,
    is_delete tinyint default 0
);
create index if not exists idx_agent_msg_conv on agent_message(conversation_id, create_time);

-- RAG 知识库文档表（与 V7__add_rag_document.sql 同步维护）
create table if not exists rag_document (
    id bigint auto_increment primary key,
    title varchar(200) null,
    source varchar(512) not null,
    text longtext null,
    user_id bigint null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null,
    constraint uq_rag_doc_source unique (source)
);

-- 漫画手帐三表（与 V9__create_comic_tables.sql 同步维护）
create table if not exists comic_book (
    id bigint auto_increment primary key,
    user_id bigint not null,
    book_name varchar(64) not null,
    default_style varchar(32) default 'powder' not null,
    is_delete tinyint default 0 not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null,
    constraint uq_comic_book_user_name unique (user_id, book_name)
);
create table if not exists comic_episode (
    id bigint auto_increment primary key,
    book_id bigint not null,
    episode_no int not null,
    title varchar(128) null,
    input_type varchar(16) default 'daily' not null,
    input_summary varchar(512) null,
    style varchar(32) default 'powder' not null,
    route_result longtext null,
    storyboard_result longtext null,
    image_prompts longtext null,
    layout_result longtext null,
    page_html longtext null,
    png_url varchar(512) null,
    is_delete tinyint default 0 not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null
);
create table if not exists comic_monthly_volume (
    id bigint auto_increment primary key,
    book_id bigint not null,
    year_month char(7) not null,
    episode_count int default 0 not null,
    index_html longtext null,
    cover_title varchar(128) null,
    is_delete tinyint default 0 not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null,
    constraint uq_comic_monthly_book_ym unique (book_id, year_month)
);
