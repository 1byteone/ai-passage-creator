package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.RagDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 当前项目研发知识库的只读混合检索服务。 */
@Service
@RequiredArgsConstructor
public class RagKnowledgeBaseService {

    private final RagDocumentStore documentStore;
    private final RagService ragService;

    public record KnowledgeHit(String title, String content, double score, String source,
                               String sourcePath, String domain, String documentKind,
                               String status, String commitSha, String sectionPath,
                               Long documentId, String projectKey, String sourceType,
                               String branchName, Integer lineStart, Integer lineEnd) {
        public KnowledgeHit(String title, String content, double score, String source,
                            String sourcePath, String domain, String documentKind,
                            String status, String commitSha, String sectionPath,
                            Long documentId, String projectKey, String sourceType,
                            String branchName) {
            this(title, content, score, source, sourcePath, domain, documentKind, status,
                    commitSha, sectionPath, documentId, projectKey, sourceType, branchName, null, null);
        }
    }

    /** 关键词和向量召回合并，按 source 去重，返回可直接展示的引用信息。 */
    public List<KnowledgeHit> search(String query, Long userId, int topK) {
        int limit = Math.max(1, Math.min(topK <= 0 ? 5 : topK, 20));
        int recallLimit = Math.min(limit * 3, 60);
        Map<String, KnowledgeHit> merged = new LinkedHashMap<>();
        for (RagDocument document : documentStore.searchActive(query, recallLimit)) {
            merged.merge(document.getSource(), fromDocument(document, keywordScore(document, query)),
                    (existing, candidate) -> candidate.score() > existing.score() ? candidate : existing);
        }
        for (RagService.RagHit hit : ragService.searchKnowledge(query, userId, recallLimit, documentStore.activeBatchId())) {
            KnowledgeHit vectorHit = new KnowledgeHit(hit.title(), hit.content(), hit.score(),
                    hit.refId(), "", "", "reference", "ACTIVE", "", "",
                    null, "ai-passage-creator", "GIT", "", null, null);
            merged.merge(hit.refId(), vectorHit,
                    (existing, candidate) -> candidate.score() > existing.score() ? candidate : existing);
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(KnowledgeHit::score).reversed())
                .limit(limit)
                .toList();
    }

    private KnowledgeHit fromDocument(RagDocument document, double score) {
        return new KnowledgeHit(document.getTitle(), document.getText(), score, document.getSource(),
                document.getSourcePath(), document.getDomain(), document.getDocumentKind(), document.getStatus(),
                document.getCommitSha(), document.getSectionPath(), document.getId(),
                document.getProjectKey(), document.getSourceType(), document.getBranchName(),
                document.getLineStart(), document.getLineEnd());
    }

    private double keywordScore(RagDocument document, String query) {
        String value = query == null ? "" : query.trim().toLowerCase();
        if (value.isBlank()) return 0.0;
        String title = lower(document.getTitle());
        String source = lower(document.getSource());
        String sourcePath = lower(document.getSourcePath());
        String sectionPath = lower(document.getSectionPath());
        String text = lower(document.getText());
        if (title.equals(value)) return 1.0;
        if (title.contains(value)) return 0.96;
        if (sourcePath.contains(value) || source.contains(value)) return 0.88;
        if (sectionPath.contains(value)) return 0.82;
        if (text.contains(value)) return 0.68;
        return 0.0;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
