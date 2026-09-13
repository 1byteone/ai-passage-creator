package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.RagDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagKnowledgeBaseServiceTest {

    @Test
    void search_mergesKeywordAndVectorHitsBySource() {
        RagDocumentStore documentStore = mock(RagDocumentStore.class);
        RagService ragService = mock(RagService.class);
        RagDocument document = RagDocument.builder()
                .title("项目规范").source("git:AGENTS.md#0").text("安全和质量规则")
                .sourcePath("AGENTS.md").domain("standard").documentKind("project-rule")
                .status(RagDocumentStore.STATUS_ACTIVE).commitSha("abc").sectionPath("项目规范").build();
        when(documentStore.searchActive(anyString(), anyInt())).thenReturn(List.of(document));
        when(ragService.search(anyString(), anyString(), anyLong(), anyInt()))
                .thenReturn(List.of(new RagService.RagHit("git:AGENTS.md#0", "项目规范", "安全和质量规则", 0.8, "document")));

        List<RagKnowledgeBaseService.KnowledgeHit> hits =
                new RagKnowledgeBaseService(documentStore, ragService).search("安全", 1L, 5);

        assertEquals(1, hits.size());
        assertEquals("AGENTS.md", hits.get(0).sourcePath());
        assertEquals("abc", hits.get(0).commitSha());
    }
}
