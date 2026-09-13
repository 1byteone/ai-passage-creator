package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.RagDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagKnowledgeBaseServiceTest {

    @Test
    void search_mergesKeywordAndVectorHitsBySource() {
        RagDocumentStore documentStore = mock(RagDocumentStore.class);
        RagService ragService = mock(RagService.class);
        RagDocument document = RagDocument.builder()
                .id(7L).title("项目规范").source("git:AGENTS.md#0").text("安全和质量规则")
                .sourcePath("AGENTS.md").domain("standard").documentKind("project-rule")
                .status(RagDocumentStore.STATUS_ACTIVE).commitSha("abc").sectionPath("项目规范")
                .projectKey("ai-passage-creator").sourceType("GIT").branchName("dev_rag").build();
        when(documentStore.searchActive(anyString(), anyInt())).thenReturn(List.of(document));
        when(ragService.search(anyString(), anyString(), anyLong(), anyInt()))
                .thenReturn(List.of(new RagService.RagHit("git:AGENTS.md#0", "项目规范", "安全和质量规则", 0.8, "document")));

        List<RagKnowledgeBaseService.KnowledgeHit> hits =
                new RagKnowledgeBaseService(documentStore, ragService).search("安全", 1L, 5);

        assertEquals(1, hits.size());
        assertEquals("AGENTS.md", hits.get(0).sourcePath());
        assertEquals("abc", hits.get(0).commitSha());
        assertEquals(7L, hits.get(0).documentId());
        assertEquals("ai-passage-creator", hits.get(0).projectKey());
        assertEquals("GIT", hits.get(0).sourceType());
        assertEquals("dev_rag", hits.get(0).branchName());
    }

    @Test
    void search_ranksTitleAndPathMatchesAboveBodyMatches() {
        RagDocumentStore documentStore = mock(RagDocumentStore.class);
        RagService ragService = mock(RagService.class);
        RagDocument bodyMatch = RagDocument.builder()
                .title("开发说明").source("git:development.md#0")
                .sourcePath("docs/development.md").sectionPath("索引流程")
                .text("这里介绍项目规范和实现细节").status(RagDocumentStore.STATUS_ACTIVE).build();
        RagDocument titleMatch = RagDocument.builder()
                .title("项目规范").source("git:standards.md#0")
                .sourcePath("docs/standards.md").sectionPath("项目规范")
                .text("其他说明").status(RagDocumentStore.STATUS_ACTIVE).build();
        when(documentStore.searchActive(anyString(), anyInt())).thenReturn(List.of(bodyMatch, titleMatch));
        when(ragService.searchKnowledge(anyString(), anyLong(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        List<RagKnowledgeBaseService.KnowledgeHit> hits =
                new RagKnowledgeBaseService(documentStore, ragService).search("项目规范", 1L, 5);

        assertEquals("项目规范", hits.get(0).title());
        assertEquals(1.0, hits.get(0).score());
        verify(documentStore).searchActive("项目规范", 15);
        verify(ragService).searchKnowledge("项目规范", 1L, 15, null);
    }

    @Test
    void search_sameSourceKeepsHigherVectorScore() {
        RagDocumentStore documentStore = mock(RagDocumentStore.class);
        RagService ragService = mock(RagService.class);
        RagDocument document = RagDocument.builder()
                .title("研发知识").source("git:knowledge.md#0").text("正文")
                .sourcePath("knowledge.md").status(RagDocumentStore.STATUS_ACTIVE).build();
        when(documentStore.searchActive(anyString(), anyInt())).thenReturn(List.of(document));
        when(ragService.searchKnowledge(anyString(), anyLong(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new RagService.RagHit("git:knowledge.md#0", "研发知识", "正文", 1.2, "document")));

        List<RagKnowledgeBaseService.KnowledgeHit> hits =
                new RagKnowledgeBaseService(documentStore, ragService).search("无关词", 1L, 5);

        assertEquals(1, hits.size());
        assertEquals(1.2, hits.get(0).score());
    }
}
