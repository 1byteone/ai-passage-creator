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
                               String status, String commitSha, String sectionPath) {
    }

    /** 关键词和向量召回合并，按 source 去重，返回可直接展示的引用信息。 */
    public List<KnowledgeHit> search(String query, Long userId, int topK) {
        int limit = Math.max(1, Math.min(topK <= 0 ? 5 : topK, 20));
        Map<String, KnowledgeHit> merged = new LinkedHashMap<>();
        for (RagDocument document : documentStore.searchActive(query, limit)) {
            merged.put(document.getSource(), fromDocument(document, keywordScore(document, query)));
        }
        for (RagService.RagHit hit : ragService.searchKnowledge(query, userId, limit, documentStore.activeBatchId())) {
            merged.putIfAbsent(hit.refId(), new KnowledgeHit(hit.title(), hit.content(), hit.score(),
                    hit.refId(), "", "", "reference", "ACTIVE", "", ""));
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(KnowledgeHit::score).reversed())
                .limit(limit)
                .toList();
    }

    private KnowledgeHit fromDocument(RagDocument document, double score) {
        return new KnowledgeHit(document.getTitle(), document.getText(), score, document.getSource(),
                document.getSourcePath(), document.getDomain(), document.getDocumentKind(), document.getStatus(),
                document.getCommitSha(), document.getSectionPath());
    }

    private double keywordScore(RagDocument document, String query) {
        String value = query == null ? "" : query.trim().toLowerCase();
        if (value.isBlank()) return 0.0;
        String title = document.getTitle() == null ? "" : document.getTitle().toLowerCase();
        String source = document.getSource() == null ? "" : document.getSource().toLowerCase();
        if (title.contains(value) || source.contains(value)) return 1.0;
        return 0.85;
    }
}
