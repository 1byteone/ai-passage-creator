-- HITL 检查点持久化：含确认阶段的 Skill 在执行暂停时写入检查点，
-- 应用重启后仍可从该表恢复待确认的执行（threadId=executionId）。
create table if not exists skill_checkpoint (
    id bigint auto_increment primary key,
    thread_id varchar(64) not null,
    checkpoint_id varchar(64) not null,
    node_id varchar(255) null,
    next_node_id varchar(255) null,
    state_data longblob null,
    released tinyint default 0 not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null
);
