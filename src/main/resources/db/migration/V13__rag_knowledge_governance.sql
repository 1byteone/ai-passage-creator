-- RAG 研发知识库 MVP：来源、领域、审核状态和 Git 版本元数据。
-- V7 is immutable; add the governance columns for existing and new databases.
ALTER TABLE rag_document ADD COLUMN source_type varchar(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE rag_document ADD COLUMN domain varchar(32) NOT NULL DEFAULT 'business';
ALTER TABLE rag_document ADD COLUMN document_kind varchar(32) NOT NULL DEFAULT 'reference';
ALTER TABLE rag_document ADD COLUMN status varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW';
ALTER TABLE rag_document ADD COLUMN branch_name varchar(128) NULL;
ALTER TABLE rag_document ADD COLUMN commit_sha varchar(64) NULL;
ALTER TABLE rag_document ADD COLUMN source_path varchar(512) NULL;
ALTER TABLE rag_document ADD COLUMN section_path varchar(256) NULL;
ALTER TABLE rag_document ADD COLUMN checksum varchar(64) NULL;
ALTER TABLE rag_document ADD COLUMN reviewer_id bigint NULL;
ALTER TABLE rag_document ADD COLUMN reviewed_at datetime NULL;
ALTER TABLE rag_document ADD COLUMN indexed_at datetime NULL;

-- V7 的旧文档原本已可被检索，升级时保留其行为；新手工文档仍使用默认 PENDING_REVIEW。
UPDATE rag_document SET source_type = 'MANUAL', status = 'ACTIVE'
WHERE source_type IS NULL OR status = 'PENDING_REVIEW';
