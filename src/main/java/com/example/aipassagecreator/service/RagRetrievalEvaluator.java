package com.example.aipassagecreator.service;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 研发知识库离线检索评测器。
 * <p>只计算召回、排序和引用完整性，不调用模型，也不改变线上检索状态。</p>
 */
public final class RagRetrievalEvaluator {

    private RagRetrievalEvaluator() {
    }

    public record Case(String id, String query, List<String> goldSources) {
    }

    public record Report(int totalCases, int hitCases, double recallAtK, double mrrAtK,
                         int citationCompleteCases, double citationCoverage,
                         int projectViolations) {
    }

    public static Report evaluate(List<Case> cases,
                                  Function<String, List<RagKnowledgeBaseService.KnowledgeHit>> searcher,
                                  int topK, String expectedProjectKey) {
        Objects.requireNonNull(cases, "cases");
        Objects.requireNonNull(searcher, "searcher");
        int k = Math.max(1, Math.min(topK, 20));
        int hitCases = 0;
        int citationCompleteCases = 0;
        int projectViolations = 0;
        double reciprocalRankSum = 0.0;

        for (Case evaluationCase : cases) {
            List<RagKnowledgeBaseService.KnowledgeHit> hits = searcher.apply(evaluationCase.query());
            List<String> goldSources = evaluationCase.goldSources() == null ? List.of() : evaluationCase.goldSources();
            int firstGoldRank = -1;
            for (int i = 0; i < Math.min(k, hits.size()); i++) {
                RagKnowledgeBaseService.KnowledgeHit hit = hits.get(i);
                if (expectedProjectKey != null && !expectedProjectKey.equals(hit.projectKey())) {
                    projectViolations++;
                }
                if (firstGoldRank < 0 && goldSources.contains(hit.source())) {
                    firstGoldRank = i + 1;
                    if (hasCompleteCitation(hit)) {
                        citationCompleteCases++;
                    }
                }
            }
            if (firstGoldRank > 0) {
                hitCases++;
                reciprocalRankSum += 1.0 / firstGoldRank;
            }
        }
        int total = cases.size();
        return new Report(total, hitCases, ratio(hitCases, total),
                total == 0 ? 0.0 : reciprocalRankSum / total,
                citationCompleteCases, ratio(citationCompleteCases, total), projectViolations);
    }

    private static boolean hasCompleteCitation(RagKnowledgeBaseService.KnowledgeHit hit) {
        return nonBlank(hit.sourcePath()) && nonBlank(hit.sectionPath()) && nonBlank(hit.commitSha());
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }
}
