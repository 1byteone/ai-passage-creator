-- V8: agent_message 复合索引（idx_agent_msg_conv）— MySQL 专属
--
-- 背景
--   V6（db/migration，可移植）用 `create table if not exists` 建 agent_message，
--   但 MySQL 不支持 `CREATE INDEX IF NOT EXISTS`，不能在可移植迁移里直接建索引，
--   否则 H2（MODE=MySQL 兼容测试）会因语法不兼容失败。
--
-- 方案
--   与 V3 同模式：本文件位于 db/vendor/mysql/（MySQL 专属），由
--   spring.flyway.locations 的 {vendor} 占位符驱动，独立目录避免被 db/migration
--   递归扫描拉到 H2 误执行。用存储过程探测 information_schema 幂等建索引
--   （已存在则跳过，兼容全新库与已手工建表的旧库）。
--
-- 版本号说明
--   声明为 V8 而非 V6/V7：Flyway 合并 db/migration 与 db/vendor/mysql 后版本必须唯一
--   （V6=agent_conversation 表、V7=rag_document 表已被占用），且本索引必须晚于
--   V6 建表执行（V8 > V6），保证 agent_message 已存在。合并后版本集：1,2,3,4,5,6,7,8。

DROP PROCEDURE IF EXISTS ensure_agent_msg_idx;

DELIMITER //
CREATE PROCEDURE ensure_agent_msg_idx()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'agent_message'
                     AND index_name = 'idx_agent_msg_conv') THEN
        CREATE INDEX idx_agent_msg_conv ON agent_message(conversation_id, create_time);
    END IF;
END //
DELIMITER ;

CALL ensure_agent_msg_idx();
DROP PROCEDURE ensure_agent_msg_idx;
