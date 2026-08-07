package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagDocumentMapper;
import com.example.aipassagecreator.model.po.RagDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagDocumentStoreTest {

    @Test
    @DisplayName("upsert — 同 source 幂等：先删旧再插（不累积）")
    void upsert_sameSource_doesNotAccumulate() {
        RagDocumentMapper mapper = mock(RagDocumentMapper.class);
        RagDocumentStore store = new RagDocumentStore(mapper, mock(RagService.class));

        store.upsert("标题", "src://a", "正文内容", 1L);

        verify(mapper).deleteByQuery(any());
        verify(mapper).insert(any(RagDocument.class));
        ArgumentCaptor<RagDocument> captor = ArgumentCaptor.forClass(RagDocument.class);
        verify(mapper).insert(captor.capture());
        assertEquals("src://a", captor.getValue().getSource());
    }

    @Test
    @DisplayName("upsert — 正文先过 sanitize 再入库")
    void upsert_sanitizesText() {
        RagDocumentMapper mapper = mock(RagDocumentMapper.class);
        RagService ragService = mock(RagService.class);
        when(ragService.sanitizeForIndexPublic("<script>x</script>正常内容")).thenReturn("正常内容");
        RagDocumentStore store = new RagDocumentStore(mapper, ragService);

        store.upsert("t", "s", "<script>x</script>正常内容", 1L);

        ArgumentCaptor<RagDocument> captor = ArgumentCaptor.forClass(RagDocument.class);
        verify(mapper).insert(captor.capture());
        assertEquals("正常内容", captor.getValue().getText());
    }
}
