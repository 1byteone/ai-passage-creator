package com.example.aipassagecreator.service;

import com.example.aipassagecreator.agent.ArticleAgentOrchestrator;
import com.example.aipassagecreator.agent.config.AgentConfig;
import com.example.aipassagecreator.enums.ArticlePhaseEnum;
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.ArticleQuality;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ArticleAsyncService 单元测试 — 3 阶段异步编排的分支路径与 SSE 推送。
 */
@ExtendWith(MockitoExtension.class)
class ArticleAsyncServiceTest {

    private static final String TASK_ID = "task-async-1";

    @Mock
    private ArticleAgentService articleAgentService;

    @Mock
    private SseEmitterManager sseEmitterManager;

    @Mock
    private ArticleService articleService;

    @Mock
    private ArticleAgentOrchestrator articleAgentOrchestrator;

    @Mock
    private AgentConfig agentConfig;

    @Mock
    private ArticleQualityGateService articleQualityGateService;

    @Mock
    private ContentQualityService contentQualityService;

    @Mock
    private RagService ragService;

    @InjectMocks
    private ArticleAsyncService articleAsyncService;

    // ──────────────────── executePhase1 ────────────────────

    @Test
    @DisplayName("阶段1 — 编排器启用时走 orchestrator 并推送 TITLES_GENERATED")
    void executePhase1_orchestratorEnabled_usesOrchestrator() {
        when(agentConfig.isOrchestratorEnabled()).thenReturn(true);
        doAnswer(invocation -> {
            ArticleState state = invocation.getArgument(0);
            ArticleState.TitleOption option = new ArticleState.TitleOption();
            option.setMainTitle("标题一");
            option.setSubTitle("副标题一");
            state.setTitleOptions(List.of(option));
            return null;
        }).when(articleAgentOrchestrator).executePhase1_GenerateTitles(any(ArticleState.class), any());

        articleAsyncService.executePhase1(TASK_ID, "主题", "tech", "default");

        verify(articleAgentOrchestrator).executePhase1_GenerateTitles(any(ArticleState.class), any());
        verify(articleAgentService, never()).executePhase1_GenerateTitles(any(), any());
        verify(articleService).updateArticleStatus(eq(TASK_ID), eq(ArticleStatusEnum.PROCESSING), eq(null));
        verify(articleService).saveTitleOptions(eq(TASK_ID), anyList());
        // executePhase1 内 updatePhase 被调用两次（TITLE_GENERATING → TITLE_SELECTING），聚焦最终阶段
        verify(articleService).updatePhase(eq(TASK_ID), eq(ArticlePhaseEnum.TITLE_SELECTING));
        verify(sseEmitterManager).send(eq(TASK_ID), anyString());
    }

    @Test
    @DisplayName("阶段1 — 编排器禁用时回退到 ArticleAgentService")
    void executePhase1_orchestratorDisabled_fallsBack() {
        when(agentConfig.isOrchestratorEnabled()).thenReturn(false);
        doAnswer(invocation -> {
            ArticleState state = invocation.getArgument(0);
            state.setTitleOptions(List.of(new ArticleState.TitleOption()));
            return null;
        }).when(articleAgentService).executePhase1_GenerateTitles(any(ArticleState.class), any());

        articleAsyncService.executePhase1(TASK_ID, "主题", "tech", "default");

        verify(articleAgentService).executePhase1_GenerateTitles(any(ArticleState.class), any());
        verify(articleAgentOrchestrator, never()).executePhase1_GenerateTitles(any(), any());
        verify(sseEmitterManager).send(eq(TASK_ID), anyString());
    }

    @Test
    @DisplayName("阶段1 — 异常时更新 FAILED 并推送 ERROR")
    void executePhase1_exception_updatesFailedAndSendsError() {
        when(agentConfig.isOrchestratorEnabled()).thenReturn(true);
        doAnswer(invocation -> {
            throw new RuntimeException("LLM 调用失败");
        }).when(articleAgentOrchestrator).executePhase1_GenerateTitles(any(ArticleState.class), any());

        articleAsyncService.executePhase1(TASK_ID, "主题", "tech", "default");

        verify(articleService).updateArticleStatus(eq(TASK_ID), eq(ArticleStatusEnum.FAILED), anyString());
        verify(sseEmitterManager).send(eq(TASK_ID), anyString());
        verify(sseEmitterManager).complete(TASK_ID);
    }

    // ──────────────────── executePhase2 ────────────────────

    @Test
    @DisplayName("阶段2 — 从 DB 加载文章构建 state 并推送 OUTLINE_GENERATED")
    void executePhase2_loadsArticleAndSendsOutline() {
        Article article = articleWithTitle();
        when(agentConfig.isOrchestratorEnabled()).thenReturn(true);
        when(articleService.getByTaskId(TASK_ID)).thenReturn(article);
        doAnswer(invocation -> {
            ArticleState state = invocation.getArgument(0);
            ArticleState.OutlineResult outline = new ArticleState.OutlineResult();
            ArticleState.OutlineSection section = new ArticleState.OutlineSection();
            section.setSection(1);
            section.setTitle("第一章");
            section.setPoints(List.of("p1"));
            outline.setSections(List.of(section));
            state.setOutline(outline);
            return null;
        }).when(articleAgentOrchestrator).executePhase2_GenerateOutline(any(ArticleState.class), any());

        articleAsyncService.executePhase2(TASK_ID);

        verify(articleAgentOrchestrator).executePhase2_GenerateOutline(any(ArticleState.class), any());
        // 保存大纲 + 更新阶段 + 推送
        verify(articleService, times(2)).getByTaskId(TASK_ID);
        verify(articleService).updatePhase(eq(TASK_ID), any());
        verify(sseEmitterManager).send(eq(TASK_ID), anyString());
    }

    // ──────────────────── executePhase3 ────────────────────

    @Test
    @DisplayName("阶段3 — 应用质量门并推送 QUALITY_CHECKED + ALL_COMPLETE")
    void executePhase3_appliesQualityGate_sendsComplete() {
        Article article = articleWithTitle();
        article.setUserId(1L);
        when(agentConfig.isOrchestratorEnabled()).thenReturn(true);
        when(articleService.getByTaskId(TASK_ID)).thenReturn(article);
        when(articleQualityGateService.checkAndDetox(any(ArticleState.class), eq(TASK_ID), eq(1L)))
                .thenReturn(new ArticleQualityGateService.GateResult(90, true, false, List.of()));
        when(articleQualityGateService.isVipOrAdmin(1L)).thenReturn(false);

        articleAsyncService.executePhase3(TASK_ID);

        verify(articleQualityGateService).checkAndDetox(any(ArticleState.class), eq(TASK_ID), eq(1L));
        verify(articleService).saveArticleContent(eq(TASK_ID), any(ArticleState.class));
        verify(articleService).updateArticleStatus(eq(TASK_ID), eq(ArticleStatusEnum.COMPLETED), eq(null));
        // 非 VIP 不触发爆款评分
        verify(contentQualityService, never()).evaluateViral(anyString(), anyString(), any());
        // 推送质量报告 + 完成消息
        verify(sseEmitterManager, times(2)).send(eq(TASK_ID), anyString());
        verify(sseEmitterManager).complete(TASK_ID);
    }

    @Test
    @DisplayName("阶段3 — VIP 用户额外触发爆款评分并附带 viralScore")
    void executePhase3_vipUser_triggersViralScore() {
        Article article = articleWithTitle();
        article.setUserId(1L);
        when(agentConfig.isOrchestratorEnabled()).thenReturn(true);
        when(articleService.getByTaskId(TASK_ID)).thenReturn(article);
        when(articleQualityGateService.checkAndDetox(any(ArticleState.class), eq(TASK_ID), eq(1L)))
                .thenReturn(new ArticleQualityGateService.GateResult(100, true, false, List.of()));
        when(articleQualityGateService.isVipOrAdmin(1L)).thenReturn(true);
        when(contentQualityService.evaluateViral(TASK_ID, "default", 1L))
                .thenReturn(ArticleQuality.builder().viralScore(new java.math.BigDecimal("88")).build());

        articleAsyncService.executePhase3(TASK_ID);

        verify(contentQualityService).evaluateViral(TASK_ID, "default", 1L);
        verify(sseEmitterManager, times(2)).send(eq(TASK_ID), anyString());
        verify(sseEmitterManager).complete(TASK_ID);
    }

    @Test
    @DisplayName("阶段3 — 文章不存在时进入失败分支")
    void executePhase3_articleNotFound_failsGracefully() {
        when(agentConfig.isOrchestratorEnabled()).thenReturn(true);
        when(articleService.getByTaskId(TASK_ID)).thenReturn(null);

        articleAsyncService.executePhase3(TASK_ID);

        verify(articleService).updateArticleStatus(eq(TASK_ID), eq(ArticleStatusEnum.FAILED), anyString());
        verify(sseEmitterManager).send(eq(TASK_ID), anyString());
        verify(sseEmitterManager).complete(TASK_ID);
    }

    // ──────────────────── 工具方法 ────────────────────

    private Article articleWithTitle() {
        Article article = new Article();
        article.setTaskId(TASK_ID);
        article.setMainTitle("主标题");
        article.setSubTitle("副标题");
        article.setStyle("tech");
        article.setMethodology("default");
        return article;
    }
}
