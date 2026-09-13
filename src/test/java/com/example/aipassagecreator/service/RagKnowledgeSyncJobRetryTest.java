package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagSyncJobMapper;
import com.example.aipassagecreator.model.po.RagSyncJob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagKnowledgeSyncJobRetryTest {
    @Test
    void retryCreatesNewBatchAndCleansFailedBatch() {
        RagSyncJobMapper mapper = mock(RagSyncJobMapper.class);
        RagKnowledgeSyncService syncService = mock(RagKnowledgeSyncService.class);
        RagDocumentStore documentStore = mock(RagDocumentStore.class);
        RagKnowledgeSyncJobService service = new RagKnowledgeSyncJobService(mapper, syncService, documentStore);
        RagSyncJob failed = RagSyncJob.builder().id(8L).projectKey("ai-passage-creator")
                .status(RagSyncJob.STATUS_FAILED).retryCount(1).build();
        when(mapper.selectOneById(8L)).thenReturn(failed);
        when(mapper.selectOneByQuery(any())).thenReturn(null);
        when(mapper.insert(any(RagSyncJob.class))).thenAnswer(invocation -> {
            RagSyncJob job = invocation.getArgument(0);
            job.setId(9L);
            return 1;
        });

        RagSyncJob retry = service.retry(8L, 1L);

        assertEquals(9L, retry.getId());
        assertEquals(2, retry.getRetryCount());
        assertEquals(8L, retry.getRetryOfJobId());
        verify(documentStore).deleteBatch("8");
    }

    @Test
    void retryRejectsNonFailedJob() {
        RagSyncJobMapper mapper = mock(RagSyncJobMapper.class);
        RagKnowledgeSyncJobService service = new RagKnowledgeSyncJobService(mapper,
                mock(RagKnowledgeSyncService.class), mock(RagDocumentStore.class));
        when(mapper.selectOneById(8L)).thenReturn(RagSyncJob.builder().id(8L)
                .projectKey("ai-passage-creator").status(RagSyncJob.STATUS_SUCCEEDED).build());

        assertNull(service.retry(8L, 1L));
        verify(mapper, never()).insert(any());
    }
}
