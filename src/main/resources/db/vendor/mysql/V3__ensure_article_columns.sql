-- V3: 为既有 article 表补齐缺失列
--
-- 背景
--   V1 用 `CREATE TABLE IF NOT EXISTS` 全幂等建表，但不会给"已存在的旧表"补列。
--   若部署库在 methodology / enabledImageMethods 等列加入 schema 前就已建表，
--   将缺失这些列 → POST /article/create 插入时报 "Column 'xxx' not found" → 50000。
--
-- 方案
--   MySQL 不支持 `ADD COLUMN IF NOT EXISTS`，故用存储过程探测 information_schema，
--   仅补缺失列；已存在的列跳过（幂等，兼容全新库与旧库）。
--   本文件位于 db/vendor/mysql/（MySQL 专属），由 spring.flyway.locations 的
--   {vendor} 占位符驱动；独立目录避免被 db/migration 递归扫描拉到 H2 误执行。

DROP PROCEDURE IF EXISTS ensure_article_columns;

DELIMITER //
CREATE PROCEDURE ensure_article_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'article'
                     AND column_name = 'methodology') THEN
        ALTER TABLE article ADD COLUMN methodology varchar(64) DEFAULT 'default' NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'article'
                     AND column_name = 'userDescription') THEN
        ALTER TABLE article ADD COLUMN userDescription text NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'article'
                     AND column_name = 'enabledImageMethods') THEN
        ALTER TABLE article ADD COLUMN enabledImageMethods json NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'article'
                     AND column_name = 'titleOptions') THEN
        ALTER TABLE article ADD COLUMN titleOptions json NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'article'
                     AND column_name = 'outline') THEN
        ALTER TABLE article ADD COLUMN outline json NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'article'
                     AND column_name = 'fullContent') THEN
        ALTER TABLE article ADD COLUMN fullContent longtext NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'article'
                     AND column_name = 'coverImage') THEN
        ALTER TABLE article ADD COLUMN coverImage varchar(512) NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'article'
                     AND column_name = 'images') THEN
        ALTER TABLE article ADD COLUMN images json NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'article'
                     AND column_name = 'completedTime') THEN
        ALTER TABLE article ADD COLUMN completedTime datetime NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'article'
                     AND column_name = 'methodology_used') THEN
        ALTER TABLE article ADD COLUMN methodology_used varchar(64) NULL;
    END IF;
END //
DELIMITER ;

CALL ensure_article_columns();
DROP PROCEDURE ensure_article_columns;
