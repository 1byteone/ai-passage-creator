package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagDocumentMapper;
import com.example.aipassagecreator.mapper.RagSyncJobMapper;
import com.example.aipassagecreator.model.po.RagDocument;
import com.example.aipassagecreator.model.po.RagSyncJob;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/** RAG 知识库文档存储 — rag_document 表 CRUD（与向量库联动） */
@Slf4j
@Service
public class RagDocumentStore {

    private final RagDocumentMapper mapper;
    private final RagService ragService;
    private final RagSyncJobMapper syncJobMapper;

    public RagDocumentStore(RagDocumentMapper mapper, RagService ragService) {
        this(mapper, ragService, null);
    }

    @Autowired
    public RagDocumentStore(RagDocumentMapper mapper, RagService ragService, RagSyncJobMapper syncJobMapper) {
        this.mapper = mapper;
        this.ragService = ragService;
        this.syncJobMapper = syncJobMapper;
    }

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
                    .projectKey("ai-passage-creator")
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
                "sourcePath", doc.getSourcePath() == null ? "" : doc.getSourcePath(),
                "projectKey", "ai-passage-creator"));
        doc.setIndexedAt(now);
        mapper.update(doc);
        return true;
    }

    /** Git 同步入口：Git 文档已由提交评审，直接作为当前项目有效知识。 */
    public void upsertGit(String title, String source, String text, String domain, String documentKind,
                          String branchName, String commitSha, String sourcePath, String sectionPath,
                          String checksum) {
        upsertGit(title, source, text, domain, documentKind, branchName, commitSha, sourcePath, sectionPath,
                checksum, null);
    }

    public void upsertGit(String title, String source, String text, String domain, String documentKind,
                          String branchName, String commitSha, String sourcePath, String sectionPath,
                          String checksum, String batchId) {
        if (source == null || source.isBlank() || text == null || text.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (batchId != null) {
            mapper.deleteByQuery(QueryWrapper.create().eq(RagDocument::getSource, source)
                    .eq(RagDocument::getBatchId, batchId));
        } else {
            mapper.deleteByQuery(QueryWrapper.create().eq(RagDocument::getSource, source));
        }
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
                .batchId(batchId)
                .projectKey("ai-passage-creator")
                .reviewedAt(now)
                .indexedAt(now)
                .createTime(now)
                .updateTime(now)
                .build();
        mapper.insert(doc);
        if (batchId == null) {
            ragService.deleteBySource(source);
        }
        String vectorSource = batchId == null ? source : batchId + ":" + source;
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("sourceType", "GIT");
        metadata.put("domain", doc.getDomain());
        metadata.put("documentKind", doc.getDocumentKind());
        metadata.put("status", STATUS_ACTIVE);
        metadata.put("branchName", branchName == null ? "" : branchName);
        metadata.put("commitSha", commitSha == null ? "" : commitSha);
        metadata.put("sourcePath", sourcePath == null ? "" : sourcePath);
        metadata.put("sectionPath", sectionPath == null ? "" : sectionPath);
        metadata.put("batchId", batchId == null ? "" : batchId);
        metadata.put("projectKey", "ai-passage-creator");
        metadata.put("vectorSource", vectorSource);
        ragService.indexDocument(doc.getTitle(), source, doc.getText(), metadata);
    }

    /** 将成功完成的批次切换为当前项目有效版本。旧批次保留，失败时不会被覆盖。 */
    public void activateBatch(String batchId) {
        if (batchId == null || batchId.isBlank()) return;
        syncJobMapper.updateByQuery(RagSyncJob.builder().active(false).updateTime(LocalDateTime.now()).build(),
                QueryWrapper.create().eq(RagSyncJob::getProjectKey, "ai-passage-creator")
                        .eq(RagSyncJob::getActive, true));
        syncJobMapper.updateByQuery(RagSyncJob.builder().active(true).updateTime(LocalDateTime.now()).build(),
                QueryWrapper.create().eq(RagSyncJob::getId, Long.valueOf(batchId)));
    }

    public String activeBatchId() {
        if (syncJobMapper == null) return null;
        RagSyncJob job = syncJobMapper.selectOneByQuery(QueryWrapper.create()
                .eq(RagSyncJob::getProjectKey, "ai-passage-creator")
                .eq(RagSyncJob::getActive, true)
                .eq(RagSyncJob::getStatus, RagSyncJob.STATUS_SUCCEEDED)
                .orderBy(RagSyncJob::getFinishedAt, false).limit(1));
        return job == null || job.getId() == null ? null : String.valueOf(job.getId());
    }

    /** 清理未激活同步批次的索引残留，调用方必须先完成 active 状态校验。 */
    public void deleteBatch(String batchId) {
        if (batchId == null || batchId.isBlank()) return;
        mapper.deleteByQuery(QueryWrapper.create().eq(RagDocument::getBatchId, batchId));
        ragService.deleteByBatchId(batchId);
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
        String activeBatch = activeBatchId();
        QueryWrapper wrapper = QueryWrapper.create().eq(RagDocument::getStatus, STATUS_ACTIVE)
                .and((Consumer<QueryWrapper>) q -> {
                    q.eq(RagDocument::getSourceType, "MANUAL");
                    if (activeBatch != null) {
                        q.or((Consumer<QueryWrapper>) r -> r.eq(RagDocument::getBatchId, activeBatch));
                    } else {
                        q.or((Consumer<QueryWrapper>) r -> r.isNull(RagDocument::getBatchId));
                    }
                });
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
