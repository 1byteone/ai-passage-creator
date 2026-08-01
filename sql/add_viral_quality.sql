-- 爆款评测列：article_quality 扩展（Task 7）
ALTER TABLE article_quality ADD COLUMN score_type varchar(16) DEFAULT 'GENERIC' NOT NULL;
ALTER TABLE article_quality ADD COLUMN user_id bigint NULL;
ALTER TABLE article_quality ADD COLUMN viral_score decimal(5,2) NULL;
ALTER TABLE article_quality ADD COLUMN viral_scores JSON NULL;
ALTER TABLE article_quality ADD COLUMN title_strategy_hit varchar(32) NULL;
ALTER TABLE article_quality ADD COLUMN methodology_used varchar(64) NULL;
ALTER TABLE article_quality ADD COLUMN content_hash varchar(64) NULL;
ALTER TABLE article_quality ADD COLUMN version_no int DEFAULT 1 NULL;
CREATE UNIQUE INDEX idx_aq_task_score_type ON article_quality(task_id, score_type);
