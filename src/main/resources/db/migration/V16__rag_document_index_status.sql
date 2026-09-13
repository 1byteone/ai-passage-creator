-- RAG Phase 2: 文档索引状态、失败原因和重试次数。
ALTER TABLE rag_document ADD COLUMN index_error varchar(2000) NULL;
ALTER TABLE rag_document ADD COLUMN index_attempts int NOT NULL DEFAULT 0;

CREATE INDEX idx_rag_document_index_status ON rag_document(status, index_attempts);
