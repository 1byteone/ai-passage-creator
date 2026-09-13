-- RAG 文档引用源文件行号，供章节级审计和人工复核使用。
ALTER TABLE rag_document ADD COLUMN line_start int NULL;
ALTER TABLE rag_document ADD COLUMN line_end int NULL;
