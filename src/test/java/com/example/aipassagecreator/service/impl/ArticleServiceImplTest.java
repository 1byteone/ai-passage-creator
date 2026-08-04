package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.enums.ArticlePhaseEnum;
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.ArticleVO;
import com.example.aipassagecreator.service.ArticleAgentService;
import com.example.aipassagecreator.service.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ArticleServiceImpl 单元测试 — 创作任务创建、阶段守卫、权限校验、内容保存。
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceImplTest {

    private static final String TASK_ID = "task-svc-1";

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private QuotaService quotaService;

    @Mock
    private ArticleAgentService articleAgentService;

    @InjectMocks
    private ArticleServiceImpl articleService;

    @BeforeEach
    void setUp() {
        // Mockito 不会将 mock 注入继承自 MyBatis-Flex ServiceImpl 的 mapper 字段，需手动注入
        ReflectionTestUtils.setField(articleService, "mapper", articleMapper);
    }

    // ──────────────────── 工具方法 ────────────────────

    private User user(Long id, String role, Integer quota) {
        return User.builder().id(id).userRole(role).quota(quota).build();
    }

    private Article articleWith(String taskId, Long userId, ArticlePhaseEnum phase) {
        return Article.builder()
                .taskId(taskId)
                .userId(userId)
                .phase(phase != null ? phase.getValue() : null)
                .build();
    }

    // ──────────────────── createArticleTask ────────────────────

    @Test
    @DisplayName("创建任务 — 普通用户默认配图方式为 PEXELS/MERMAID/ICONIFY/EMOJI_PACK")
    void createArticleTask_normalUser_setsDefaultImageMethods() {
        User loginUser = user(1L, "user", 5);
        when(articleMapper.insert(any(Article.class), anyBoolean())).thenReturn(1);

        String taskId = articleService.createArticleTask("主题", "tech", "default", null, loginUser);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).insert(captor.capture(), anyBoolean());
        Article saved = captor.getValue();

        assertNotNull(taskId);
        assertEquals(loginUser.getId(), saved.getUserId());
        assertEquals(ArticleStatusEnum.PENDING.getValue(), saved.getStatus());
        assertEquals(ArticlePhaseEnum.PENDING.getValue(), saved.getPhase());
        assertTrue(saved.getEnabledImageMethods().contains("PEXELS"));
        assertTrue(saved.getEnabledImageMethods().contains("EMOJI_PACK"));
    }

    @Test
    @DisplayName("创建任务 — 普通用户选择 NANO_BANANA 被拒绝")
    void createArticleTask_normalUserAiImage_throws() {
        User loginUser = user(1L, "user", 5);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.createArticleTask("主题", "tech", "default",
                        List.of("NANO_BANANA"), loginUser));
        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), ex.getCode());
        verify(articleMapper, never()).insert(any(), anyBoolean());
    }

    @Test
    @DisplayName("创建任务 — VIP 用户可使用 AI 生图")
    void createArticleTask_vipUserAiImage_allowed() {
        User loginUser = user(1L, "vip", null);
        when(articleMapper.insert(any(Article.class), anyBoolean())).thenReturn(1);

        String taskId = articleService.createArticleTask("主题", "tech", "default",
                List.of("NANO_BANANA"), loginUser);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).insert(captor.capture(), anyBoolean());
        assertTrue(captor.getValue().getEnabledImageMethods().contains("NANO_BANANA"));
        assertNotNull(taskId);
    }

    @Test
    @DisplayName("创建任务 — VIP 未选配图方式时不设置默认限制")
    void createArticleTask_vipNoMethods_noRestriction() {
        User loginUser = user(1L, "vip", null);
        when(articleMapper.insert(any(Article.class), anyBoolean())).thenReturn(1);

        articleService.createArticleTask("主题", "tech", "default", null, loginUser);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).insert(captor.capture(), anyBoolean());
        assertEquals(null, captor.getValue().getEnabledImageMethods(), "VIP 不限制配图方式");
    }

    // ──────────────────── createArticleTaskWithQuotaCheck ────────────────────

    @Test
    @DisplayName("创建任务+扣配额 — 配额充足则创建成功")
    void createArticleTaskWithQuotaCheck_quotaOk_createsTask() {
        User loginUser = user(1L, "user", 5);
        when(articleMapper.insert(any(Article.class), anyBoolean())).thenReturn(1);

        String taskId = articleService.createArticleTaskWithQuotaCheck(
                "主题", "tech", "default", null, loginUser);

        verify(quotaService).checkAndConsumeQuota(loginUser);
        assertNotNull(taskId);
    }

    @Test
    @DisplayName("创建任务+扣配额 — 配额不足则抛异常且不落库")
    void createArticleTaskWithQuotaCheck_quotaInsufficient_throws() {
        User loginUser = user(1L, "user", 0);
        doThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "配额不足"))
                .when(quotaService).checkAndConsumeQuota(loginUser);

        assertThrows(BusinessException.class,
                () -> articleService.createArticleTaskWithQuotaCheck(
                        "主题", "tech", "default", null, loginUser));
        verify(articleMapper, never()).insert(any(), anyBoolean());
    }

    // ──────────────────── confirmTitle ────────────────────

    @Test
    @DisplayName("确认标题 — 阶段正确则保存标题并转入 OUTLINE_GENERATING")
    void confirmTitle_success_updatesPhase() {
        Article article = articleWith(TASK_ID, 1L, ArticlePhaseEnum.TITLE_SELECTING);
        when(articleMapper.selectOneByQuery(any())).thenReturn(article);

        articleService.confirmTitle(TASK_ID, "主标题", "副标题", "补充", user(1L, "user", 5));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).update(captor.capture(), anyBoolean());
        assertEquals("主标题", captor.getValue().getMainTitle());
        assertEquals(ArticlePhaseEnum.OUTLINE_GENERATING.getValue(), captor.getValue().getPhase());
    }

    @Test
    @DisplayName("确认标题 — 阶段错误被拒绝")
    void confirmTitle_wrongPhase_throws() {
        Article article = articleWith(TASK_ID, 1L, ArticlePhaseEnum.PENDING);
        when(articleMapper.selectOneByQuery(any())).thenReturn(article);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.confirmTitle(TASK_ID, "主标题", "副标题", null, user(1L, "user", 5)));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        verify(articleMapper, never()).update(any(), anyBoolean());
    }

    @Test
    @DisplayName("确认标题 — 非本人操作被拒绝")
    void confirmTitle_notOwner_throws() {
        Article article = articleWith(TASK_ID, 1L, ArticlePhaseEnum.TITLE_SELECTING);
        when(articleMapper.selectOneByQuery(any())).thenReturn(article);

        assertThrows(BusinessException.class,
                () -> articleService.confirmTitle(TASK_ID, "主标题", "副标题", null, user(2L, "user", 5)));
        verify(articleMapper, never()).update(any(), anyBoolean());
    }

    @Test
    @DisplayName("确认标题 — 文章不存在抛出 NOT_FOUND")
    void confirmTitle_articleNotFound_throws() {
        when(articleMapper.selectOneByQuery(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.confirmTitle("ghost", "主标题", "副标题", null, user(1L, "user", 5)));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    // ──────────────────── confirmOutline ────────────────────

    @Test
    @DisplayName("确认大纲 — 阶段正确则保存大纲并转入 CONTENT_GENERATING")
    void confirmOutline_success_updatesPhase() {
        Article article = articleWith(TASK_ID, 1L, ArticlePhaseEnum.OUTLINE_EDITING);
        when(articleMapper.selectOneByQuery(any())).thenReturn(article);

        ArticleState.OutlineSection section = new ArticleState.OutlineSection();
        section.setSection(1);
        section.setTitle("第一章");
        section.setPoints(List.of("要点1", "要点2"));

        articleService.confirmOutline(TASK_ID, List.of(section), user(1L, "user", 5));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).update(captor.capture(), anyBoolean());
        assertEquals(ArticlePhaseEnum.CONTENT_GENERATING.getValue(), captor.getValue().getPhase());
        assertTrue(captor.getValue().getOutline().contains("第一章"));
    }

    @Test
    @DisplayName("确认大纲 — 阶段错误被拒绝")
    void confirmOutline_wrongPhase_throws() {
        Article article = articleWith(TASK_ID, 1L, ArticlePhaseEnum.TITLE_SELECTING);
        when(articleMapper.selectOneByQuery(any())).thenReturn(article);

        assertThrows(BusinessException.class,
                () -> articleService.confirmOutline(TASK_ID, List.of(), user(1L, "user", 5)));
        verify(articleMapper, never()).update(any(), anyBoolean());
    }

    // ──────────────────── saveArticleContent ────────────────────

    @Test
    @DisplayName("保存文章内容 — 封面图提取 position=1 的图片 URL")
    void saveArticleContent_success_persistsAllFields() {
        Article article = articleWith(TASK_ID, 1L, ArticlePhaseEnum.CONTENT_GENERATING);
        when(articleMapper.selectOneByQuery(any())).thenReturn(article);

        ArticleState state = new ArticleState();
        state.setTaskId(TASK_ID);

        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle("主标题");
        title.setSubTitle("副标题");
        state.setTitle(title);

        ArticleState.OutlineResult outline = new ArticleState.OutlineResult();
        ArticleState.OutlineSection section = new ArticleState.OutlineSection();
        section.setSection(1);
        section.setTitle("章节一");
        section.setPoints(List.of("p1"));
        outline.setSections(List.of(section));
        state.setOutline(outline);

        state.setContent("正文内容");
        state.setFullContent("完整图文");

        ArticleState.ImageResult cover = new ArticleState.ImageResult();
        cover.setPosition(1);
        cover.setUrl("https://cos.example.com/cover.png");
        ArticleState.ImageResult other = new ArticleState.ImageResult();
        other.setPosition(2);
        other.setUrl("https://cos.example.com/inline.png");
        state.setImages(List.of(cover, other));

        articleService.saveArticleContent(TASK_ID, state);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).update(captor.capture(), anyBoolean());
        Article saved = captor.getValue();
        assertEquals("主标题", saved.getMainTitle());
        assertEquals("正文内容", saved.getContent());
        assertEquals("完整图文", saved.getFullContent());
        assertEquals("https://cos.example.com/cover.png", saved.getCoverImage(), "封面应为 position=1 的图");
        assertNotNull(saved.getCompletedTime(), "完成时应记录 completedTime");
    }

    // ──────────────────── getArticleDetail / deleteArticle ────────────────────

    @Test
    @DisplayName("文章详情 — 本人可查看")
    void getArticleDetail_owner_success() {
        Article article = articleWith(TASK_ID, 1L, ArticlePhaseEnum.TITLE_SELECTING);
        article.setTopic("主题");
        when(articleMapper.selectOneByQuery(any())).thenReturn(article);

        ArticleVO vo = articleService.getArticleDetail(TASK_ID, user(1L, "user", 5));
        assertEquals(TASK_ID, vo.getTaskId());
    }

    @Test
    @DisplayName("文章详情 — 非本人查看被拒绝")
    void getArticleDetail_notOwner_throws() {
        Article article = articleWith(TASK_ID, 1L, ArticlePhaseEnum.TITLE_SELECTING);
        when(articleMapper.selectOneByQuery(any())).thenReturn(article);

        assertThrows(BusinessException.class,
                () -> articleService.getArticleDetail(TASK_ID, user(2L, "user", 5)));
    }

    @Test
    @DisplayName("删除文章 — 本人逻辑删除成功")
    void deleteArticle_owner_success() {
        Article article = articleWith(TASK_ID, 1L, null);
        article.setId(9L);
        when(articleMapper.selectOneById(anyLong())).thenReturn(article);
        when(articleMapper.deleteById(9L)).thenReturn(1);

        boolean deleted = articleService.deleteArticle(9L, user(1L, "user", 5));
        assertTrue(deleted);
    }

    @Test
    @DisplayName("删除文章 — 非本人删除被拒绝")
    void deleteArticle_notOwner_throws() {
        Article article = articleWith(TASK_ID, 1L, null);
        article.setId(9L);
        when(articleMapper.selectOneById(anyLong())).thenReturn(article);

        assertThrows(BusinessException.class,
                () -> articleService.deleteArticle(9L, user(2L, "user", 5)));
        verify(articleMapper, never()).deleteById(anyLong());
    }
}
