package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagSyncJobMapper;
import com.example.aipassagecreator.model.po.RagSyncJob;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagKnowledgeSyncJobServiceTest {

    @Test
    void executeAsync_successActivatesOnlyCompletedBatch() {
        RagSyncJobMapper mapper = mock(RagSyncJobMapper.class);
        RagKnowledgeSyncService syncService = mock(RagKnowledgeSyncService.class);
        RagDocumentStore documentStore = mock(RagDocumentStore.class);
        RagKnowledgeSyncJobService service = new RagKnowledgeSyncJobService(mapper, syncService, documentStore);
        RagSyncJob job = RagSyncJob.builder().id(7L).status(RagSyncJob.STATUS_QUEUED).build();
        when(mapper.selectOneById(7L)).thenReturn(job);
        when(syncService.syncBatch("7")).thenReturn(new RagKnowledgeSyncService.SyncResult(
                3, 12, "dev_rag", "abc123", List.of("git:README.md#0")));

        service.executeAsync(7L);

        assertEquals(RagSyncJob.STATUS_SUCCEEDED, job.getStatus());
        assertEquals("abc123", job.getCommitSha());
        verify(documentStore).activateBatch("7");
    }

    @Test
    void executeAsync_failureKeepsPreviousActiveBatch() {
        RagSyncJobMapper mapper = mock(RagSyncJobMapper.class);
        RagKnowledgeSyncService syncService = mock(RagKnowledgeSyncService.class);
        RagDocumentStore documentStore = mock(RagDocumentStore.class);
        RagKnowledgeSyncJobService service = new RagKnowledgeSyncJobService(mapper, syncService, documentStore);
        RagSyncJob job = RagSyncJob.builder().id(8L).status(RagSyncJob.STATUS_QUEUED).build();
        when(mapper.selectOneById(8L)).thenReturn(job);
        doThrow(new IllegalStateException("embedding timeout")).when(syncService).syncBatch("8");

        service.executeAsync(8L);

        assertEquals(RagSyncJob.STATUS_FAILED, job.getStatus());
        assertEquals("embedding timeout", job.getErrorMessage());
        verify(documentStore, never()).activateBatch(any());
    }
}
