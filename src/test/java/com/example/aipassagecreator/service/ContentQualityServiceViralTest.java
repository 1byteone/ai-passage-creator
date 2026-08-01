package com.example.aipassagecreator.service;

import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.mapper.ArticleQualityMapper;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.skill.ModelRouter;
import com.example.aipassagecreator.utils.GsonUtils;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 爆款维度评测 evaluateViral 测试
 * <p>在真实 H2 + Spring 上下文上验证权重归一化、幂等落库与前置校验。</p>
 */
@SpringBootTest
class ContentQualityServiceViralTest {

    @MockitoBean
    private ModelRouter modelRouter;

    @Autowired
    private ContentQualityService contentQualityService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private ArticleQualityMapper qualityMapper;

    private ChatModel mockChatModel;

    @BeforeEach
    void setUp() {
        mockChatModel = org.mockito.Mockito.mock(ChatModel.class);
        when(modelRouter.resolveWithFallback(any(), any())).thenReturn(mockChatModel);
        when(modelRouter.resolveModelName(any(), any())).thenReturn("mock-model");
    }

    private void mockResponse(String json) {
        ChatResponse resp = new ChatResponse(List.of(
                new Generation(new AssistantMessage(json))));
        when(mockChatModel.call(any(Prompt.class))).thenReturn(resp);
    }

    /** 直接插入一条 COMPLETED 文章，绕开配额/异步链路，聚焦 evaluateViral */
    private String insertCompletedArticle() {
        String taskId = "viral-test-" + System.nanoTime();
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(2L);
        article.setTopic("爆款评测测试选题");
        article.setMainTitle("测试主标题");
        article.setSubTitle("测试副标题");
        article.setContent("这是一段用于爆款评测的测试文章内容，包含多个自然段，用于验证快照与评分流程。".repeat(20));
        article.setStatus(ArticleStatusEnum.COMPLETED.getValue());
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        article.setIsDelete(0);
        articleMapper.insert(article);
        return taskId;
    }

    private String insertArticleWithStatus(ArticleStatusEnum status) {
        String taskId = "viral-pending-" + System.nanoTime();
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(2L);
        article.setTopic("状态校验测试选题");
        article.setContent("内容");
        article.setStatus(status.getValue());
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        article.setIsDelete(0);
        articleMapper.insert(article);
        return taskId;
    }

    @Test
    void tryFixJson_stripsFence() {
        // 验证工具层增强（评测容错解析前置依赖）
        String fixed = GsonUtils.tryFixJson("```json\n{\"a\":1}\n```");
        assertEquals("{\"a\":1}", fixed);
    }

    @Test
    void evaluateViral_normalizesWeights() {
        String taskId = insertCompletedArticle();
        // 维度: emotionalTrigger=85,goldenSentence=75,interactionHook=60,persuasion=90,titleStrategy=80
        // 权重(default): 20,15,15,15,10 → Σweight=75, Σscore*weight=5875 → viralScore=78.33
        mockResponse("""
            {"structureScore":80,"logicScore":78,"languageScore":82,"seoScore":70,"readabilityScore":85,"overallScore":79,
             "viral":{"emotionalTrigger":85,"goldenSentence":75,"interactionHook":60,"persuasion":90,"titleStrategy":80},
             "titleStrategyHit":"curiosityGap","suggestions":["加强互动"]}""");

        ArticleQuality q = contentQualityService.evaluateViral(taskId, "default", 2L);
        assertNotNull(q);
        assertEquals(0, new BigDecimal("78.33").compareTo(q.getViralScore()),
                "Σscore*weight=5875, Σweight=75 → 78.33");
        assertEquals("VIRAL", q.getScoreType());
        assertEquals("default", q.getMethodologyUsed());
        assertEquals("curiosityGap", q.getTitleStrategyHit());
        assertEquals(Integer.valueOf(1), q.getVersionNo());

        // 幂等：同 taskId 二次评测仍只有一行 VIRAL 记录（delete+insert），versionNo 递增
        ArticleQuality again = contentQualityService.evaluateViral(taskId, "default", 2L);
        assertNotNull(again);
        assertEquals(Integer.valueOf(2), again.getVersionNo());
        // 每任务仅一行 VIRAL 评测（新行替换旧行，而非累积）
        List<ArticleQuality> allViralRows = qualityMapper.selectListByQuery(
                QueryWrapper.create().eq("task_id", taskId).eq("score_type", "VIRAL"));
        assertEquals(1, allViralRows.size(), "VIRAL 评测每任务仅保留一行");

        // 归属用户落库
        ArticleQuality latest = contentQualityService.getLatestViral(taskId);
        assertNotNull(latest);
        assertEquals(Long.valueOf(2L), latest.getUserId());
        assertEquals(0, new BigDecimal("78.33").compareTo(latest.getViralScore()));
    }

    @Test
    void evaluateViral_articleNotFound_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> contentQualityService.evaluateViral("__nonexistent__", "default", 1L));
    }

    @Test
    void evaluateViral_notCompleted_throws() {
        String taskId = insertArticleWithStatus(ArticleStatusEnum.PENDING);
        assertThrows(IllegalArgumentException.class,
                () -> contentQualityService.evaluateViral(taskId, "default", 2L));
    }
}
