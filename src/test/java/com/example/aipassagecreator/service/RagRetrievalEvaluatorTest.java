package com.example.aipassagecreator.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagRetrievalEvaluatorTest {

    @Test
    void evaluate_calculatesRecallMrrCitationAndProjectViolations() {
        RagKnowledgeBaseService.KnowledgeHit correct = hit("git:standards.md#0", "standards.md", "规范", "abc", "ai-passage-creator");
        RagKnowledgeBaseService.KnowledgeHit wrongProject = hit("git:other.md#0", "other.md", "其他", "def", "other-project");
        List<RagRetrievalEvaluator.Case> cases = List.of(
                new RagRetrievalEvaluator.Case("S01", "技术栈", List.of(correct.source())),
                new RagRetrievalEvaluator.Case("S02", "不存在的问题", List.of("git:missing.md#0")));

        RagRetrievalEvaluator.Report report = RagRetrievalEvaluator.evaluate(cases, query ->
                "技术栈".equals(query) ? List.of(wrongProject, correct) : List.of(), 5, "ai-passage-creator");

        assertEquals(2, report.totalCases());
        assertEquals(1, report.hitCases());
        assertEquals(0.5, report.recallAtK());
        assertEquals(0.25, report.mrrAtK());
        assertEquals(1, report.citationCompleteCases());
        assertEquals(0.5, report.citationCoverage());
        assertEquals(1, report.projectViolations());
    }

    private RagKnowledgeBaseService.KnowledgeHit hit(String source, String path, String section,
                                                       String commit, String project) {
        return new RagKnowledgeBaseService.KnowledgeHit("标题", "正文", 0.9, source, path,
                "standard", "reference", "INDEXED", commit, section, 1L, project, "GIT", "dev_rag");
    }
}
