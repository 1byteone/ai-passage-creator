-- RAG Phase 2: 持久化同步任务和批次元数据。
CREATE TABLE IF NOT EXISTS rag_sync_job (
    id bigint auto_increment primary key,
    project_key varchar(128) NOT NULL,
    branch_name varchar(128) NULL,
    commit_sha varchar(64) NULL,
    status varchar(24) NOT NULL,
    total_files int NOT NULL DEFAULT 0,
    processed_files int NOT NULL DEFAULT 0,
    total_sections int NOT NULL DEFAULT 0,
    indexed_sections int NOT NULL DEFAULT 0,
    error_message varchar(2000) NULL,
    active boolean NOT NULL DEFAULT false,
    created_by bigint NULL,
    started_at datetime NULL,
    finished_at datetime NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE rag_document ADD COLUMN batch_id varchar(64) NULL;
ALTER TABLE rag_document ADD COLUMN project_key varchar(128) NOT NULL DEFAULT 'ai-passage-creator';
ALTER TABLE rag_document DROP INDEX uq_rag_doc_source;

CREATE INDEX idx_rag_sync_job_project_status ON rag_sync_job(project_key, status, active);
CREATE INDEX idx_rag_document_batch_status ON rag_document(batch_id, status);
CREATE INDEX idx_rag_document_project_source ON rag_document(project_key, source_type);
