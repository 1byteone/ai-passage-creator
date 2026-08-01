-- 爆款方法论引擎 v2: 防止并发评测双插 (配合 delete+insert 幂等)
CREATE UNIQUE INDEX IF NOT EXISTS idx_aq_task_score_type ON article_quality(task_id, score_type);
