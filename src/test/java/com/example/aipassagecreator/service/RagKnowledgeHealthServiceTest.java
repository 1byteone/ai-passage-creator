package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagDocumentMapper;
import com.example.aipassagecreator.mapper.RagSyncJobMapper;
import com.example.aipassagecreator.model.po.RagDocument;
import com.example.aipassagecreator.model.po.RagSyncJob;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagKnowledgeHealthServiceTest {

    @Test
    void getHealth_reportsFailedDocumentsAndActiveVersion() {
        RagDocumentMapper documentMapper = mock(RagDocumentMapper.class);
        RagSyncJobMapper jobMapper = mock(RagSyncJobMapper.class);
        when(documentMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(
                RagDocument.builder().status(RagDocumentStore.STATUS_INDEXED).build(),
                RagDocument.builder().status(RagDocumentStore.STATUS_INDEX_FAILED).build(),
                RagDocument.builder().status(RagDocumentStore.STATUS_PENDING_REVIEW).build()));
        when(jobMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(RagSyncJob.builder()
                .id(8L).commitSha("abc123").branchName("dev_rag").status(RagSyncJob.STATUS_SUCCEEDED)
                .active(true).build());

        RagKnowledgeHealthService.Health health = new RagKnowledgeHealthService(documentMapper, jobMapper).getHealth();

        assertEquals("DEGRADED", health.status());
        assertEquals(3L, health.totalDocuments());
        assertEquals(1L, health.indexedDocuments());
        assertEquals(1L, health.failedDocuments());
        assertEquals(1L, health.pendingReviewDocuments());
        assertEquals("abc123", health.activeCommitSha());
    }
}
