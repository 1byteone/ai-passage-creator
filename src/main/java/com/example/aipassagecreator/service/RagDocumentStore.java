package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagDocumentMapper;
import com.example.aipassagecreator.model.po.RagDocument;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/** RAG 知识库文档存储 — rag_document 表 CRUD（与向量库联动） */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagDocumentStore {

    private final RagDocumentMapper mapper;
    private final RagService ragService;

    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 手工上传/覆盖：同 source 幂等，先进入待审核，不写入正式向量索引。 */
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
                    .sourceType("MANUAL")
                    .domain("business")
                    .documentKind("reference")
                    .status(STATUS_PENDING_REVIEW)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            mapper.insert(doc);
            // 待审核文档不进入正式检索；覆盖时清理旧向量，避免旧版本继续可见。
            ragService.deleteBySource(source);
            log.info("RAG 知识库已登记待审核文档: source={}, title={}", source, title);
        } catch (Exception e) {
            log.warn("RAG 知识库 upsert 失败: source={}, err={}", source, e.getMessage());
        }
    }

    /** 审核通过后才进入正式检索。MVP 只允许管理员调用。 */
    public boolean approve(Long id, Long reviewerId) {
        RagDocument doc = mapper.selectOneById(id);
        if (doc == null || !STATUS_PENDING_REVIEW.equals(doc.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        doc.setStatus(STATUS_ACTIVE);
        doc.setReviewerId(reviewerId);
        doc.setReviewedAt(now);
        doc.setUpdateTime(now);
        mapper.update(doc);
        ragService.indexDocument(doc.getTitle(), doc.getSource(), doc.getText(), java.util.Map.of(
                "sourceType", doc.getSourceType(),
                "domain", doc.getDomain(),
                "documentKind", doc.getDocumentKind(),
                "status", doc.getStatus(),
                "sourcePath", doc.getSourcePath() == null ? "" : doc.getSourcePath()));
        doc.setIndexedAt(now);
        mapper.update(doc);
        return true;
    }

    /** Git 同步入口：Git 文档已由提交评审，直接作为当前项目有效知识。 */
    public void upsertGit(String title, String source, String text, String domain, String documentKind,
                          String branchName, String commitSha, String sourcePath, String sectionPath,
                          String checksum) {
        if (source == null || source.isBlank() || text == null || text.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.deleteByQuery(QueryWrapper.create().eq(RagDocument::getSource, source));
        RagDocument doc = RagDocument.builder()
                .title(title == null ? "" : title)
                .source(source)
                .text(ragService.sanitizeForIndexPublic(text))
                .sourceType("GIT")
                .domain(domain == null ? "standard" : domain)
                .documentKind(documentKind == null ? "reference" : documentKind)
                .status(STATUS_ACTIVE)
                .branchName(branchName)
                .commitSha(commitSha)
                .sourcePath(sourcePath)
                .sectionPath(sectionPath)
                .checksum(checksum)
                .reviewedAt(now)
                .indexedAt(now)
                .createTime(now)
                .updateTime(now)
                .build();
        mapper.insert(doc);
        ragService.deleteBySource(source);
        ragService.indexDocument(doc.getTitle(), source, doc.getText(), java.util.Map.of(
                "sourceType", "GIT",
                "domain", doc.getDomain(),
                "documentKind", doc.getDocumentKind(),
                "status", STATUS_ACTIVE,
                "branchName", branchName == null ? "" : branchName,
                "commitSha", commitSha == null ? "" : commitSha,
                "sourcePath", sourcePath == null ? "" : sourcePath,
                "sectionPath", sectionPath == null ? "" : sectionPath));
    }

    /** 文件重新同步前清除该文件产生的章节记录和向量。 */
    public void deleteBySourcePrefix(String sourcePrefix) {
        if (sourcePrefix == null || sourcePrefix.isBlank()) {
            return;
        }
        List<RagDocument> documents = mapper.selectListByQuery(
                QueryWrapper.create().like(RagDocument::getSource, sourcePrefix));
        for (RagDocument document : documents) {
            deleteById(document.getId());
        }
    }

    /** 只返回审核通过的文档，供关键词检索使用。 */
    public List<RagDocument> searchActive(String keyword, int limit) {
        String value = keyword == null ? "" : keyword.trim();
        QueryWrapper wrapper = QueryWrapper.create().eq(RagDocument::getStatus, STATUS_ACTIVE);
        if (!value.isBlank()) {
            wrapper.and((Consumer<QueryWrapper>) q -> {
                q.like(RagDocument::getTitle, value);
                q.or((Consumer<QueryWrapper>) r -> r.like(RagDocument::getSource, value));
                q.or((Consumer<QueryWrapper>) r -> r.like(RagDocument::getText, value));
            });
        }
        wrapper.orderBy(RagDocument::getUpdateTime, false);
        return mapper.selectListByQuery(wrapper).stream().limit(Math.max(1, Math.min(limit, 20))).toList();
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
