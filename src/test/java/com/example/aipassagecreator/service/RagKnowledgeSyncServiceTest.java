package com.example.aipassagecreator.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RagKnowledgeSyncServiceTest {

    @Test
    void sync_readsFixedProjectDocumentsAndCreatesSectionSources() {
        RagDocumentStore store = mock(RagDocumentStore.class);
        String root = Paths.get(".").toAbsolutePath().normalize().toString();
        RagKnowledgeSyncService service = new RagKnowledgeSyncService(store, root, "dev_rag");

        RagKnowledgeSyncService.SyncResult result = service.sync();

        assertTrue(result.files() >= 1);
        assertTrue(result.sections() >= result.files());
        assertTrue(result.sources().stream().anyMatch(source -> source.startsWith("git:AGENTS.md#")));
        verify(store, atLeast(1)).deleteBySourcePrefix(anyString());
        verify(store, atLeast(1)).upsertGit(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
