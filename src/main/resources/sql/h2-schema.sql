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
    enabledImageMethods json null,
    style varchar(50) null,
    methodology varchar(64) default 'default' null,
    mainTitle varchar(200) null,
    subTitle varchar(300) null,
    titleOptions json null,
    outline json null,
    content text null,
    fullContent text null,
    coverImage varchar(512) null,
    images json null,
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
