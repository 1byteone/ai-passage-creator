package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagDocumentMapper;
import com.example.aipassagecreator.model.po.RagDocument;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.Consumer;

/** RAG 知识库文档存储 — rag_document 表 CRUD（与向量库联动） */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagDocumentStore {

    private final RagDocumentMapper mapper;
    private final RagService ragService;

    /** 上传/覆盖：同 source 幂等（先删旧行再插），并同步向量索引 */
    public void upsert(String title, String source, String text, Long userId) {
        if (source == null || source.isBlank()) {
            return;
        }
        try {
            mapper.deleteByQuery(QueryWrapper.create().eq(RagDocument::getSource, source));
            String clean = ragService.sanitizeForIndexPublic(text);
            RagDocument doc = RagDocument.builder()
                    .title(title == null ? "" : title)
                    .source(source)
                    .text(clean)
                    .userId(userId)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            mapper.insert(doc);
            // 向量同步：先清旧 source 再索引（幂等）
            ragService.deleteBySource(source);
            ragService.indexDocument(title, source, clean);
            log.info("RAG 知识库已 upsert 文档: source={}, title={}", source, title);
        } catch (Exception e) {
            log.warn("RAG 知识库 upsert 失败: source={}, err={}", source, e.getMessage());
        }
    }

    /** 分页列表（keyword 模糊匹配 title/source） */
    public Page<RagDocument> page(long pageNum, long pageSize, String keyword) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (keyword != null && !keyword.isBlank()) {
            // LambdaGetter 的 SAM get(T) 也是单参，与 Consumer 重载产生二义性 → 显式 cast 消歧
            wrapper.and((Consumer<QueryWrapper>) q -> q.like(RagDocument::getTitle, keyword));
            wrapper.or((Consumer<QueryWrapper>) q -> q.like(RagDocument::getSource, keyword));
        }
        wrapper.orderBy(RagDocument::getCreateTime, false);
        return mapper.paginate(pageNum, pageSize, wrapper);
    }

    /** 删除：删表行 + 清向量 */
    public boolean deleteById(Long id) {
        RagDocument doc = mapper.selectOneById(id);
        if (doc == null) {
            return false;
        }
        mapper.deleteById(id);
        ragService.deleteBySource(doc.getSource());
        log.info("RAG 知识库已删除文档: id={}, source={}", id, doc.getSource());
        return true;
    }
}
