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

create index idx_userName on `user`(userName);

INSERT INTO `user` (id, userAccount, userPassword, userName, userAvatar, userProfile, userRole) VALUES
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
    create_time datetime default CURRENT_TIMESTAMP
);

create index idx_aq_task_id on article_quality(task_id);
create index idx_aq_overall_score on article_quality(overall_score);