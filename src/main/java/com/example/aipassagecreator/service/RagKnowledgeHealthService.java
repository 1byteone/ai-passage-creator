package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagDocumentMapper;
import com.example.aipassagecreator.mapper.RagSyncJobMapper;
import com.example.aipassagecreator.model.po.RagDocument;
import com.example.aipassagecreator.model.po.RagSyncJob;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 管理员只读查看研发知识库索引治理状态，不代表外部向量服务连通性。 */
@Service
@RequiredArgsConstructor
public class RagKnowledgeHealthService {

    private static final String PROJECT_KEY = "ai-passage-creator";

    public record Health(String status, String projectKey, Long activeBatchId,
                         String activeCommitSha, String activeBranchName,
                         long totalDocuments, long indexedDocuments,
                         long indexingDocuments, long failedDocuments,
                         long pendingReviewDocuments) {
    }

    public Health getHealth() {
        List<RagDocument> documents = documentMapper.selectListByQuery(QueryWrapper.create()
                .select("id", "status", "project_key", "batch_id", "commit_sha", "branch_name")
                .eq("project_key", PROJECT_KEY));
        Map<String, Long> counts = documents.stream()
                .map(RagDocument::getStatus)
                .collect(java.util.stream.Collectors.groupingBy(
                        status -> status == null ? "UNKNOWN" : status,
                        java.util.stream.Collectors.counting()));
        RagSyncJob active = syncJobMapper.selectOneByQuery(QueryWrapper.create()
                .eq("project_key", PROJECT_KEY)
                .eq("active", true)
                .eq("status", RagSyncJob.STATUS_SUCCEEDED)
                .orderBy("finished_at", false)
                .limit(1));
        long total = documents.size();
        long indexed = counts.getOrDefault(RagDocumentStore.STATUS_INDEXED, 0L)
                + counts.getOrDefault(RagDocumentStore.STATUS_ACTIVE, 0L);
        long failed = counts.getOrDefault(RagDocumentStore.STATUS_INDEX_FAILED, 0L);
        String status = total == 0 ? "EMPTY" : failed > 0 || active == null ? "DEGRADED" : "HEALTHY";
        return new Health(status, PROJECT_KEY, active == null ? null : active.getId(),
                active == null ? null : active.getCommitSha(),
                active == null ? null : active.getBranchName(), total, indexed,
                counts.getOrDefault(RagDocumentStore.STATUS_INDEXING, 0L), failed,
                counts.getOrDefault(RagDocumentStore.STATUS_PENDING_REVIEW, 0L));
    }

    private final RagDocumentMapper documentMapper;
    private final RagSyncJobMapper syncJobMapper;
}
