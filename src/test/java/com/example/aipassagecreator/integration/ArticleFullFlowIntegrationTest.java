package com.example.aipassagecreator.integration;

import com.example.aipassagecreator.agent.ArticleAgentOrchestrator;
import com.example.aipassagecreator.enums.ArticlePhaseEnum;
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.ArticleVO;
import com.example.aipassagecreator.service.ArticleAsyncService;
import com.example.aipassagecreator.service.ArticleQualityGateService;
import com.example.aipassagecreator.service.ArticleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * 创作模块全链路集成测试 — 真实 H2 数据库 + 真实 Service/SSE 编排。
 *
 * <p>{@code articleExecutor} 在测试中替换为 {@link SyncTaskExecutor}，使 {@code @Async} 阶段方法
 * 在调用线程同步执行，从而确定性验证「创建任务 → 阶段1 标题 → 确认标题 → 阶段2 大纲 → 确认大纲
 * → 阶段3 正文 → 保存 → 完成」的真实落库链路。AI Agent 编排用 {@link MockBean} 替代，避免真实 LLM。</p>
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@Transactional
class ArticleFullFlowIntegrationTest {

    @TestConfiguration
    static class SyncExecutorConfig {
        // 覆盖主配置的 articleExecutor，让 @Async 阶段方法同步执行，避免测试时序抖动
        @Bean(name = "articleExecutor")
        Executor articleExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleAsyncService articleAsyncService;

    @Autowired
    private SseEmitterManager sseEmitterManager;

    @MockBean
    private ArticleAgentOrchestrator articleAgentOrchestrator;

    @MockBean
    private ArticleQualityGateService articleQualityGateService;

    private static final long USER_ID = 100L;

    private User normalUser() {
        return User.builder().id(USER_ID).userRole("user").quota(5).build();
    }

    private User otherUser() {
        return User.builder().id(101L).userRole("user").quota(5).build();
    }

    private String createTask() {
        return articleService.createArticleTask(
                "2026年AI如何改变职场", "tech", "default", List.of("PEXELS"), normalUser());
    }

    private List<ArticleState.OutlineSection> sampleOutline() {
        ArticleState.OutlineSection s1 = new ArticleState.OutlineSection();
        s1.setSection(1);
        s1.setTitle("AI 冲击就业");
        s1.setPoints(List.of("岗位结构变化", "新技能需求"));
        ArticleState.OutlineSection s2 = new ArticleState.OutlineSection();
        s2.setSection(2);
        s2.setTitle("如何应对");
        s2.setPoints(List.of("终身学习"));
        return List.of(s1, s2);
    }

    @Test
    @DisplayName("创建任务 — 真实落库：taskId/status/phase/userId 均正确")
    void createTask_persistsToH2() {
        String taskId = createTask();

        Article saved = articleService.getByTaskId(taskId);
        assertNotNull(saved, "文章应从 H2 读回");
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(ArticleStatusEnum.PENDING.getValue(), saved.getStatus());
        assertEquals(ArticlePhaseEnum.PENDING.getValue(), saved.getPhase());
        assertTrue(saved.getEnabledImageMethods().contains("PEXELS"));
        assertEquals("tech", saved.getStyle());
        assertEquals("default", saved.getMethodology());
    }

    @Test
    @DisplayName("完整阶段流转 — 标题→大纲 真实落库 + 阶段守卫")
    void fullPhaseFlow_persistsTitleAndOutline() {
        String taskId = createTask();

        // 模拟阶段1完成：进入 TITLE_SELECTING 后确认标题
        articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_SELECTING);
        articleService.confirmTitle(taskId, "AI 重塑职场", "2026 全景展望", "强调技术原理", normalUser());
        assertEquals(ArticlePhaseEnum.OUTLINE_GENERATING.getValue(),
                articleService.getByTaskId(taskId).getPhase());

        // 模拟阶段2完成：进入 OUTLINE_EDITING 后确认大纲
        articleService.updatePhase(taskId, ArticlePhaseEnum.OUTLINE_GENERATING);
        articleService.updatePhase(taskId, ArticlePhaseEnum.OUTLINE_EDITING);
        articleService.confirmOutline(taskId, sampleOutline(), normalUser());
        Article afterOutline = articleService.getByTaskId(taskId);
        assertEquals(ArticlePhaseEnum.CONTENT_GENERATING.getValue(), afterOutline.getPhase());
        assertTrue(afterOutline.getOutline().contains("AI 冲击就业"), "大纲 JSON 应真实落库");
        assertTrue(afterOutline.getOutline().contains("岗位结构变化"));
    }

    @Test
    @DisplayName("保存完整文章 — 内容/配图/封面图 真实落库")
    void saveArticleContent_persistsAllFields() {
        String taskId = createTask();

        ArticleState state = new ArticleState();
        state.setTaskId(taskId);
        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle("AI 重塑职场");
        title.setSubTitle("2026 全景展望");
        state.setTitle(title);
        ArticleState.OutlineResult outline = new ArticleState.OutlineResult();
        outline.setSections(sampleOutline());
        state.setOutline(outline);
        state.setContent("## 第一章\nAI 正在改变职场。");
        state.setFullContent("## 第一章\nAI 正在改变职场。\n![配图](https://cos.example.com/1.png)");

        ArticleState.ImageResult cover = new ArticleState.ImageResult();
        cover.setPosition(1);
        cover.setUrl("https://cos.example.com/cover.png");
        ArticleState.ImageResult inline = new ArticleState.ImageResult();
        inline.setPosition(2);
        inline.setUrl("https://cos.example.com/inline.png");
        state.setImages(List.of(cover, inline));

        articleService.saveArticleContent(taskId, state);

        // 断言非 JSON 字段（H2 与 MySQL 行为一致）
        Article saved = articleService.getByTaskId(taskId);
        assertEquals("AI 重塑职场", saved.getMainTitle());
        assertEquals("## 第一章\nAI 正在改变职场。", saved.getContent());
        assertEquals("https://cos.example.com/cover.png", saved.getCoverImage(), "封面应为 position=1 的图");
        assertNotNull(saved.getCompletedTime(), "完成时应记录 completedTime");

        // 断言 VO 读回（H2 longtext 列与 MySQL json 列行为一致，可正常解析）
        ArticleVO vo = articleService.getArticleDetail(taskId, normalUser());
        assertEquals("AI 重塑职场", vo.getMainTitle());
        assertEquals("## 第一章\nAI 正在改变职场。", vo.getContent());
        assertEquals("https://cos.example.com/cover.png", vo.getCoverImage());
        assertNotNull(vo.getImages());
        assertEquals(2, vo.getImages().size());
        assertNotNull(vo.getOutline());
        assertEquals(2, vo.getOutline().size());
    }

    @Test
    @DisplayName("文章详情 — 本人可读回非 JSON 字段")
    void getArticleDetail_owner_readsNonJsonFields() {
        String taskId = createTask();
        articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_SELECTING);
        articleService.confirmTitle(taskId, "AI 重塑职场", "2026 全景展望", null, normalUser());

        ArticleVO vo = articleService.getArticleDetail(taskId, normalUser());
        assertEquals("AI 重塑职场", vo.getMainTitle());
        assertEquals("2026 全景展望", vo.getSubTitle());
        assertEquals(ArticlePhaseEnum.OUTLINE_GENERATING.getValue(), vo.getPhase());
    }

    @Test
    @DisplayName("权限守卫 — 非本人读取文章被拒绝")
    void getArticleDetail_deniesOtherUser() {
        String taskId = createTask();

        assertThrows(BusinessException.class,
                () -> articleService.getArticleDetail(taskId, otherUser()));
    }

    @Test
    @DisplayName("异步阶段1 — 同步执行后标题方案真实落库 + 阶段推进")
    void asyncPhase1_generatesAndPersistsTitles() {
        String taskId = createTask();

        // Mock 编排器：阶段1 产出标题方案（同步回调内填充 state）
        doAnswer(invocation -> {
            ArticleState state = invocation.getArgument(0);
            ArticleState.TitleOption o1 = new ArticleState.TitleOption();
            o1.setMainTitle("AI 重塑职场");
            o1.setSubTitle("2026 全景展望");
            ArticleState.TitleOption o2 = new ArticleState.TitleOption();
            o2.setMainTitle("程序员新战场");
            o2.setSubTitle("大模型时代生存指南");
            state.setTitleOptions(List.of(o1, o2));
            return null;
        }).when(articleAgentOrchestrator).executePhase1_GenerateTitles(any(ArticleState.class), any());

        // SyncTaskExecutor 下 @Async 同步执行，返回时阶段1已完整跑完
        articleAsyncService.executePhase1(taskId, "2026年AI如何改变职场", "tech", "default");

        Article saved = articleService.getByTaskId(taskId);
        assertEquals(ArticleStatusEnum.PROCESSING.getValue(), saved.getStatus(), "阶段1完成后应处于 PROCESSING");
        assertEquals(ArticlePhaseEnum.TITLE_SELECTING.getValue(), saved.getPhase(), "阶段1完成应进入 TITLE_SELECTING");
        assertNotNull(saved.getTitleOptions());
        assertTrue(saved.getTitleOptions().contains("AI 重塑职场"), "标题方案应真实落库");
    }

    @Test
    @DisplayName("异步阶段3 — 生成正文+质量门后状态 COMPLETED 且内容落库")
    void asyncPhase3_generatesAndPersistsContent() {
        String taskId = createTask();
        // 前置：把标题/大纲准备就绪（阶段3读取 DB 中的标题与大纲）
        articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_SELECTING);
        articleService.confirmTitle(taskId, "AI 重塑职场", "2026 全景展望", null, normalUser());
        articleService.updatePhase(taskId, ArticlePhaseEnum.OUTLINE_GENERATING);
        articleService.updatePhase(taskId, ArticlePhaseEnum.OUTLINE_EDITING);
        articleService.confirmOutline(taskId, sampleOutline(), normalUser());

        // Mock 编排器：阶段3 产出正文 + 配图
        doAnswer(invocation -> {
            ArticleState state = invocation.getArgument(0);
            state.setContent("## 第一章\nAI 正在改变职场的每个角落。");
            ArticleState.ImageResult img = new ArticleState.ImageResult();
            img.setPosition(1);
            img.setUrl("https://cos.example.com/1.png");
            state.setImages(List.of(img));
            state.setFullContent("## 第一章\nAI 正在改变职场的每个角落。\n![配图](https://cos.example.com/1.png)");
            return null;
        }).when(articleAgentOrchestrator).executePhase3_GenerateContent(any(ArticleState.class), any());

        // Mock 质量门：直接通过，避免真实 skill/LLM 调用
        doAnswer(invocation -> new ArticleQualityGateService.GateResult(90, true, false, List.of()))
                .when(articleQualityGateService).checkAndDetox(any(ArticleState.class), any(), any());
        org.mockito.Mockito.when(articleQualityGateService.isVipOrAdmin(USER_ID)).thenReturn(false);

        articleAsyncService.executePhase3(taskId);

        Article saved = articleService.getByTaskId(taskId);
        assertEquals(ArticleStatusEnum.COMPLETED.getValue(), saved.getStatus(), "阶段3完成后应 COMPLETED");
        assertEquals("## 第一章\nAI 正在改变职场的每个角落。", saved.getContent());
        assertEquals("https://cos.example.com/1.png", saved.getCoverImage());
        assertNotNull(saved.getCompletedTime());
    }

    @Test
    @DisplayName("SSE 管理器 — 创建/发送/完成 真实链路")
    void sseManager_createSendComplete() {
        String taskId = createTask();

        SseEmitter emitter = sseEmitterManager.createEmitter(taskId);
        assertNotNull(emitter);
        assertTrue(sseEmitterManager.exists(taskId), "创建后应登记");

        // 真实发送不抛异常
        sseEmitterManager.send(taskId, "{\"type\":\"TEST\"}");
        sseEmitterManager.sendHeartbeat(taskId);

        sseEmitterManager.complete(taskId);
        assertTrue(!sseEmitterManager.exists(taskId), "完成后应移除");

        // 对不存在连接的发送应静默
        sseEmitterManager.send(taskId, "{\"type\":\"AFTER\"}");
        sseEmitterManager.complete(taskId);
    }
}
