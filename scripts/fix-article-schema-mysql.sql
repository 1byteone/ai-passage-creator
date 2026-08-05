-- ============================================================
-- 修复 article 表缺失列（schema 漂移导致 POST /article/create 返回 50000）
--
-- 背景
--   V1 迁移用 `CREATE TABLE IF NOT EXISTS article` 全幂等建表，
--   但**不会给已存在的旧表补列**。若本地 MySQL 的 article 表在
--   methodology / enabledImageMethods 等列加入 schema 前就已创建，
--   这些列将一直缺失 → 创建文章时 INSERT 报
--   "Column 'METHODOLOGY' not found" → 50000 系统错误。
--
-- 用法
--   1) 先看缺哪些列：
--        SHOW COLUMNS FROM article;
--   2) 只对缺失的列执行下方对应 ALTER（MySQL 不支持 ADD COLUMN IF NOT EXISTS，
--      已存在的列执行会报 Duplicate column，属正常现象）。
--   3) 重跑应用：mvn spring-boot:run → POST /article/create 应返回 taskId。
-- ============================================================

-- 创建文章最关键的列（缺失会直接导致 create 失败）
ALTER TABLE article ADD COLUMN methodology varchar(64) DEFAULT 'default' NULL;

-- 其余可能缺失的列（按 V1__baseline.sql 定义补齐）
ALTER TABLE article ADD COLUMN userDescription text NULL;
ALTER TABLE article ADD COLUMN enabledImageMethods json NULL;
ALTER TABLE article ADD COLUMN titleOptions json NULL;
ALTER TABLE article ADD COLUMN outline json NULL;
ALTER TABLE article ADD COLUMN content text NULL;
ALTER TABLE article ADD COLUMN fullContent text NULL;
ALTER TABLE article ADD COLUMN coverImage varchar(512) NULL;
ALTER TABLE article ADD COLUMN images json NULL;
ALTER TABLE article ADD COLUMN phase varchar(50) NULL;
ALTER TABLE article ADD COLUMN errorMessage text NULL;
ALTER TABLE article ADD COLUMN completedTime datetime NULL;
ALTER TABLE article ADD COLUMN methodology_used varchar(64) NULL;
