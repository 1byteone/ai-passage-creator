package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.po.RagSyncJob;
import com.example.aipassagecreator.service.RagDocumentStore;
import com.example.aipassagecreator.service.RagKnowledgeBaseService;
import com.example.aipassagecreator.service.RagKnowledgeSyncService;
import com.example.aipassagecreator.service.RagKnowledgeSyncJobService;
import com.example.aipassagecreator.service.RagKnowledgeHealthService;
import com.example.aipassagecreator.service.RagService;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagKnowledgeControllerTest {

    @Mock
    private RagService ragService;
    @Mock
    private UserService userService;
    @Mock
    private RagDocumentStore documentStore;
    @Mock
    private RagKnowledgeBaseService knowledgeBase;
    @Mock
    private RagKnowledgeSyncService syncService;
    @Mock
    private RagKnowledgeSyncJobService syncJobService;
    @Mock
    private RagKnowledgeHealthService healthService;

    @InjectMocks
    private RagController controller;

    private MockHttpServletRequest request;
    private User admin;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        admin = new User();
        admin.setId(1L);
        admin.setUserRole("admin");
    }

    @Test
    void knowledgeSearch_defaultsTopKAndReturnsCitations() {
        RagController.RagSearchRequest input = new RagController.RagSearchRequest();
        input.setQuery("SSE 生命周期");
        RagKnowledgeBaseService.KnowledgeHit hit = new RagKnowledgeBaseService.KnowledgeHit(
                "项目规范", "引用内容", 0.9, "git:CLAUDE.md#1", "CLAUDE.md",
                "standard", "project-rule", "ACTIVE", "abc", "工程规范",
                9L, "ai-passage-creator", "GIT", "dev_rag", 12, 24);
        when(userService.getLoginUser(request)).thenReturn(admin);
        when(knowledgeBase.searchWithStatus("SSE 生命周期", 1L, 5)).thenReturn(
                new RagKnowledgeBaseService.KnowledgeSearchResult(List.of(hit), true, "CONFIRMED", "已确认"));

        var response = controller.knowledgeSearch(input, request);

        assertEquals(0, response.getCode());
        assertNotNull(response.getData());
        assertEquals(true, response.getData().confirmed());
        assertEquals("CLAUDE.md", response.getData().hits().get(0).sourcePath());
        assertEquals(12, response.getData().hits().get(0).lineStart());
        assertEquals(24, response.getData().hits().get(0).lineEnd());
        verify(knowledgeBase).searchWithStatus("SSE 生命周期", 1L, 5);
    }

    @Test
    void syncKnowledge_delegatesToReadOnlySyncService() {
        RagSyncJob result = RagSyncJob.builder().id(7L).status(RagSyncJob.STATUS_QUEUED).build();
        when(userService.getLoginUser(request)).thenReturn(admin);
        when(syncJobService.start(1L)).thenReturn(result);

        var response = controller.syncKnowledge(request);

        assertEquals(0, response.getCode());
        assertEquals(RagSyncJob.STATUS_QUEUED, response.getData().getStatus());
        verify(syncJobService).start(1L);
    }

    @Test
    void knowledgeHealth_returnsIndexGovernanceSnapshot() {
        RagKnowledgeHealthService.Health health = new RagKnowledgeHealthService.Health(
                "HEALTHY", "ai-passage-creator", 8L, "abc123", "dev_rag",
                10, 10, 0, 0, 0);
        when(healthService.getHealth()).thenReturn(health);

        var response = controller.knowledgeHealth();

        assertEquals(0, response.getCode());
        assertEquals("HEALTHY", response.getData().status());
        assertEquals("abc123", response.getData().activeCommitSha());
        verify(healthService).getHealth();
    }

    @Test
    void approveDocument_passesReviewerIdentity() {
        request = new MockHttpServletRequest();
        when(userService.getLoginUser(request)).thenReturn(admin);
        when(documentStore.approve(9L, 1L)).thenReturn(true);

        var response = controller.approveDocument(9L, request);

        assertEquals(0, response.getCode());
        assertEquals(true, response.getData());
        verify(documentStore).approve(eq(9L), eq(1L));
    }
}
