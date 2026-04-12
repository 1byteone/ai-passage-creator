-- 智能体执行日志表
create table if not exists agent_log
(
    id              bigint auto_increment comment 'id' primary key,
    taskId          varchar(64)                        not null comment '任务ID',
    agentName       varchar(50)                        not null comment '智能体名称',
    startTime       datetime                           not null comment '开始时间',
    endTime         datetime                           null comment '结束时间',
    durationMs      int                                null comment '耗时（毫秒）',
    status          varchar(20)                        not null comment '状态：SUCCESS/FAILED',
    errorMessage    text                               null comment '错误信息',
    prompt          text                               null comment '使用的Prompt',
    inputData       json                               null comment '输入数据（JSON格式）',
    outputData      json                               null comment '输出数据（JSON格式）',
    createTime      datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint     default 0              not null comment '是否删除',
    INDEX idx_taskId (taskId),
    INDEX idx_agentName (agentName),
    INDEX idx_status (status),
    INDEX idx_createTime (createTime)
    ) comment '智能体执行日志表' collate = utf8mb4_unicode_ci;
