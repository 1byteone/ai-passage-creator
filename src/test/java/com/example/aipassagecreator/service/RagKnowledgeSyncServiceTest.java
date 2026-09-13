package com.example.aipassagecreator.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class RagKnowledgeSyncServiceTest {

    @TempDir
    Path tempDir;

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
        ArgumentCaptor<Integer> starts = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> ends = ArgumentCaptor.forClass(Integer.class);
        verify(store, atLeast(1)).upsertGit(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(),
                starts.capture(), ends.capture());
        List<Integer> capturedStarts = starts.getAllValues();
        List<Integer> capturedEnds = ends.getAllValues();
        assertTrue(capturedStarts.stream().allMatch(value -> value > 0));
        assertTrue(java.util.stream.IntStream.range(0, capturedStarts.size())
                .allMatch(index -> capturedEnds.get(index) >= capturedStarts.get(index)));
    }

    @Test
    void sync_buildsHierarchyPathAndIgnoresHeadingsInsideFencedCode() throws Exception {
        Files.writeString(tempDir.resolve("AGENTS.md"), """
                # Project Rules
                overview
                ## Sync Flow
                sync details
                ```java
                # Not a heading
                ```
                """);
        RagDocumentStore store = mock(RagDocumentStore.class);
        RagKnowledgeSyncService service = new RagKnowledgeSyncService(store, tempDir.toString(), "dev_rag");

        RagKnowledgeSyncService.SyncResult result = service.sync();

        assertEquals(2, result.sections());
        ArgumentCaptor<String> paths = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).upsertGit(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), paths.capture(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        assertTrue(paths.getAllValues().contains("Project Rules"));
        assertTrue(paths.getAllValues().contains("Project Rules > Sync Flow"));
    }
}
