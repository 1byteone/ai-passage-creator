package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagDocumentMapper;
import com.example.aipassagecreator.model.po.RagDocument;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagDocumentStoreKnowledgeTest {

    @Test
    void manualUpload_staysPendingAndDoesNotIndex() {
        RagDocumentMapper mapper = mock(RagDocumentMapper.class);
        RagService ragService = mock(RagService.class);
        RagDocumentStore store = new RagDocumentStore(mapper, ragService);

        store.upsert("手工文档", "manual:test", "需要审核的内容", 1L);

        verify(mapper).insert(any(RagDocument.class));
        verify(ragService).deleteBySource("manual:test");
        verify(ragService, never()).indexDocument(any(), any(), any(), any());
    }

    @Test
    void approve_indexesOnlyPendingDocument() {
        RagDocumentMapper mapper = mock(RagDocumentMapper.class);
        RagService ragService = mock(RagService.class);
        RagDocument document = RagDocument.builder()
                .id(7L).title("研发规范").source("manual:rules").text("规则内容")
                .sourceType("MANUAL").domain("standard").documentKind("reference")
                .status(RagDocumentStore.STATUS_PENDING_REVIEW).build();
        when(mapper.selectOneById(7L)).thenReturn(document);
        RagDocumentStore store = new RagDocumentStore(mapper, ragService);

        boolean approved = store.approve(7L, 1L);

        org.junit.jupiter.api.Assertions.assertTrue(approved);
        org.junit.jupiter.api.Assertions.assertEquals(RagDocumentStore.STATUS_ACTIVE, document.getStatus());
        verify(ragService).indexDocument(any(), any(), any(), any());
        verify(mapper, org.mockito.Mockito.atLeastOnce()).update(document);
    }
}
