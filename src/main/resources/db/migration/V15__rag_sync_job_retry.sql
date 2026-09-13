-- RAG Phase 2: 同步失败重试溯源与批次清理支持。
ALTER TABLE rag_sync_job ADD COLUMN retry_count int NOT NULL DEFAULT 0;
ALTER TABLE rag_sync_job ADD COLUMN retry_of_job_id bigint NULL;

CREATE INDEX idx_rag_sync_job_retry_of ON rag_sync_job(retry_of_job_id);
