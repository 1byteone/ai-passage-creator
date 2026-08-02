package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.mapper.ArticleVersionMapper;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.ArticleVersion;
import com.example.aipassagecreator.skill.ModelRouter;
import com.example.aipassagecreator.utils.GsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章改写服务单元测试 — 聚焦改写/回退后 fullContent 与 content 同步（配图重合并）。
 *
 * <p>卡片渲染优先读 fullContent，若不重合并配图，refine 后卡片会拿到改写前的旧文本。</p>
 */
@ExtendWith(MockitoExtension.class)
class ArticleRewriteServiceImplTest {

    private static final String TASK_ID = "task-a1-2";

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private ArticleVersionMapper articleVersionMapper;

    @Mock
    private ModelRouter modelRouter;

    @InjectMocks
    private ArticleRewriteServiceImpl rewriteService;

    private ChatModel mockChatModel;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        // revertTo 不走 LLM，这两处 stub 在回退用例中不会用到，用 lenient 避免误报
        lenient().when(modelRouter.resolveWithFallback(any(), any())).thenReturn(mockChatModel);
        lenient().when(modelRouter.resolveModelName(any(), any())).thenReturn("qwen-mock");
    }

    private Article articleWithImage() {
        Article article = new Article();
        article.setTaskId(TASK_ID);
        article.setContent("原始内容\n{{IMG_1}}\n结尾");
        article.setFullContent("原始内容\n![旧图](https://cos.example.com/old.png)\n结尾");
        ArticleState.ImageResult img = new ArticleState.ImageResult();
        img.setPlaceholderId("{{IMG_1}}");
        img.setUrl("https://cos.example.com/1.png");
        img.setDescription("测试配图");
        article.setImages(GsonUtils.toJson(List.of(img)));
        return article;
    }

    private void mockRewriteResponse(String rewritten) {
        ChatResponse resp = new ChatResponse(List.of(
                new Generation(new AssistantMessage(rewritten))));
        when(mockChatModel.call(any(Prompt.class))).thenReturn(resp);
    }

    private Article capturedUpdatedArticle() {
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper, times(1)).update(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("整篇改写 — fullContent 重合并配图，与 content 同步")
    void rewrite_mergesImagesIntoFullContent() {
        when(articleMapper.selectOneByQuery(any())).thenReturn(articleWithImage());
        when(articleVersionMapper.selectOneByQuery(any())).thenReturn(null);
        mockRewriteResponse("改写后内容\n{{IMG_1}}\n新结尾");

        rewriteService.rewrite(TASK_ID, "更口语化", 1, 1L);

        Article updated = capturedUpdatedArticle();
        assertEquals("改写后内容\n{{IMG_1}}\n新结尾", updated.getContent());
        assertTrue(updated.getFullContent().contains("![测试配图](https://cos.example.com/1.png)"),
                "fullContent 应重合并配图 markdown");
        assertFalse(updated.getFullContent().contains("{{IMG_1}}"), "占位符应被替换");
        assertFalse(updated.getFullContent().contains("![旧图](https://cos.example.com/old.png)"),
                "不应残留改写前图片");
        verify(articleVersionMapper, times(1)).insert(any(ArticleVersion.class));
    }

    @Test
    @DisplayName("整篇改写 — 无配图时 fullContent 直接等于改写结果")
    void rewrite_noImages_fullContentEqualsRewritten() {
        Article article = new Article();
        article.setTaskId(TASK_ID);
        article.setContent("原始内容");
        article.setImages(null);
        when(articleMapper.selectOneByQuery(any())).thenReturn(article);
        when(articleVersionMapper.selectOneByQuery(any())).thenReturn(null);
        mockRewriteResponse("改写后内容");

        rewriteService.rewrite(TASK_ID, "更口语化", 1, 1L);

        Article updated = capturedUpdatedArticle();
        assertEquals("改写后内容", updated.getFullContent());
    }

    @Test
    @DisplayName("定向改写 — 也同步 fullContent")
    void rewriteSection_syncsFullContent() {
        when(articleMapper.selectOneByQuery(any())).thenReturn(articleWithImage());
        when(articleVersionMapper.selectOneByQuery(any())).thenReturn(null);
        mockRewriteResponse("定向改写段落\n{{IMG_1}}\n保留");

        rewriteService.rewriteSection(TASK_ID, "加强金句", null, 1L);

        Article updated = capturedUpdatedArticle();
        assertEquals("定向改写段落\n{{IMG_1}}\n保留", updated.getContent());
        assertTrue(updated.getFullContent().contains("![测试配图](https://cos.example.com/1.png)"));
    }

    @Test
    @DisplayName("回退版本 — fullContent 重合并回退内容的配图")
    void revertTo_syncsFullContent() {
        Article article = articleWithImage();
        when(articleMapper.selectOneByQuery(any())).thenReturn(article);

        ArticleVersion target = ArticleVersion.builder()
                .taskId(TASK_ID).versionNo(2).round(1)
                .content("历史版本\n{{IMG_1}}\n回到过去").build();
        ArticleVersion lastVer = ArticleVersion.builder()
                .taskId(TASK_ID).versionNo(3).round(1).content("当前版本").build();
        when(articleVersionMapper.selectOneByQuery(any())).thenReturn(target, lastVer);

        rewriteService.revertTo(TASK_ID, 2, 1L);

        Article updated = capturedUpdatedArticle();
        assertEquals("历史版本\n{{IMG_1}}\n回到过去", updated.getContent());
        assertTrue(updated.getFullContent().contains("![测试配图](https://cos.example.com/1.png)"),
                "回退后 fullContent 应重合并配图");
        assertFalse(updated.getFullContent().contains("{{IMG_1}}"));
    }
}
