package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.TopicRecommendVO;
import com.example.aipassagecreator.skill.SkillExecution;
import com.example.aipassagecreator.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TopicRecommendService 单元测试 — 混合来源聚合、AI 限流/缓存、失败降级。
 */
@ExtendWith(MockitoExtension.class)
class TopicRecommendServiceTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private RagService ragService;

    @Mock
    private SkillRegistry skillRegistry;

    @InjectMocks
    private TopicRecommendService service;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "aiEnabled", true);
        // M3 修复后 history 来源先取用户最近一篇已完成文章作检索 query，默认给一篇
        // （lenient：仅部分测试走 recommend，strict stubbing 会误判多余 stub）
        Article recent = Article.builder()
                .taskId("recent-task")
                .userId(USER_ID)
                .status("COMPLETED")
                .mainTitle("最近完成文章")
                .topic("最近选题")
                .build();
        lenient().when(articleMapper.selectOneByQuery(any())).thenReturn(recent);
    }

    private User normalUser() {
        return User.builder().id(USER_ID).userRole("user").quota(5).build();
    }

    private Map<String, Object> hotRow(String topic, long cnt) {
        return Map.of("topic", topic, "cnt", cnt);
    }

    @Test
    @DisplayName("非 refresh — 只返回平台热门 + 用户历史，不触发 AI")
    void recommend_noRefresh_skipsAi() {
        when(articleMapper.countTopicsByPopularity(anyInt()))
                .thenReturn(List.of(hotRow("AI 改变职场", 3L), hotRow("程序员竞争力", 2L)));
        when(ragService.search(anyString(), eq("article"), any(), anyInt()))
                .thenReturn(List.of(
                        new RagService.RagHit("t1", "深度思考", "content", 0.9, "article")));

        TopicRecommendVO vo = service.recommend(false, normalUser());

        assertFalse(vo.isHasAi(), "非 refresh 不应有 AI 选题");
        assertTrue(vo.getItems().stream().anyMatch(i -> "hot".equals(i.getSource())));
        assertTrue(vo.getItems().stream().anyMatch(i -> "history".equals(i.getSource())));
        // 热门先于历史
        assertEquals("hot", vo.getItems().get(0).getSource());
    }

    @Test
    @DisplayName("refresh — 触发 AI 且来源标记正确")
    void recommend_refresh_includesAi() {
        when(articleMapper.countTopicsByPopularity(anyInt())).thenReturn(List.of(hotRow("热门一", 3L)));
        when(ragService.search(anyString(), eq("article"), any(), anyInt())).thenReturn(List.of());

        SkillExecution mockExec = mock(SkillExecution.class);
        when(skillRegistry.createExecution(eq("topic-gen"), any())).thenReturn(mockExec);
        when(mockExec.getPersistedOutput()).thenReturn(
                Map.of("topicOptions", List.of(
                        Map.of("title", "AI 新爆款", "type", "洞察观点型"),
                        Map.of("title", "远程办公新趋势", "type", "实战教程型"))));

        TopicRecommendVO vo = service.recommend(true, normalUser());

        assertTrue(vo.isHasAi(), "refresh 应有 AI 选题");
        assertTrue(vo.getItems().stream().anyMatch(i -> "ai".equals(i.getSource()) && "AI 新爆款".equals(i.getText())));
        // 热门在前，AI 在后
        assertEquals("hot", vo.getItems().get(0).getSource());
        assertTrue(vo.getItems().stream().anyMatch(i -> "ai".equals(i.getSource())));
    }

    @Test
    @DisplayName("限流 — 1 分钟内多次 refresh 只触发有限次 AI")
    void recommend_rateLimit_limitsAi() {
        when(articleMapper.countTopicsByPopularity(anyInt())).thenReturn(List.of(hotRow("热门一", 3L)));
        when(ragService.search(anyString(), eq("article"), any(), anyInt())).thenReturn(List.of());

        SkillExecution mockExec = mock(SkillExecution.class);
        when(skillRegistry.createExecution(eq("topic-gen"), any())).thenReturn(mockExec);
        when(mockExec.getPersistedOutput()).thenReturn(
                Map.of("topicOptions", List.of(Map.of("title", "AI 选题", "type", "洞察观点型"))));

        // 第一次 refresh 触发 AI
        TopicRecommendVO first = service.recommend(true, normalUser());
        assertTrue(first.isHasAi());

        // 连续触发多次，后续应限流（缓存命中或限流）
        boolean sawAi = false;
        for (int i = 0; i < 5; i++) {
            TopicRecommendVO vo = service.recommend(true, normalUser());
            if (vo.isHasAi()) sawAi = true;
        }
        // 限流 2 次/分钟后，至少有一次被限流（不触发 AI）
        verify(skillRegistry, times(1)).createExecution(eq("topic-gen"), any());
    }

    @Test
    @DisplayName("AI 失败 — 静默降级为热门+历史，不抛异常")
    void recommend_aiFails_degradesGracefully() {
        when(articleMapper.countTopicsByPopularity(anyInt())).thenReturn(List.of(hotRow("热门一", 3L)));
        when(ragService.search(anyString(), eq("article"), any(), anyInt())).thenReturn(List.of());
        when(skillRegistry.createExecution(eq("topic-gen"), any()))
                .thenThrow(new RuntimeException("LLM 调用失败"));

        TopicRecommendVO vo = service.recommend(true, normalUser());

        assertFalse(vo.isHasAi(), "AI 失败不应标记 hasAi");
        assertTrue(vo.getItems().stream().anyMatch(i -> "hot".equals(i.getSource())), "应保留热门选题");
    }

    @Test
    @DisplayName("热门为空 + 历史为空 — 返回空列表不抛异常")
    void recommend_emptySources_returnsEmpty() {
        when(articleMapper.countTopicsByPopularity(anyInt())).thenReturn(List.of());
        when(ragService.search(anyString(), eq("article"), any(), anyInt())).thenReturn(List.of());

        TopicRecommendVO vo = service.recommend(false, normalUser());

        assertTrue(vo.getItems().isEmpty());
        assertFalse(vo.isHasAi());
    }

    @Test
    @DisplayName("去重 — 热门与历史重复 topic 只保留一个")
    void recommend_dedupesByText() {
        when(articleMapper.countTopicsByPopularity(anyInt()))
                .thenReturn(List.of(hotRow("重复选题", 5L)));
        when(ragService.search(anyString(), eq("article"), any(), anyInt()))
                .thenReturn(List.of(new RagService.RagHit("t1", "重复选题", "c", 0.9, "article")));

        TopicRecommendVO vo = service.recommend(false, normalUser());

        long count = vo.getItems().stream().filter(i -> "重复选题".equals(i.getText())).count();
        assertEquals(1, count, "重复 topic 应去重");
    }

    // ── 语义截断归一化 ──

    /** 反射调用私有 normalizeTopic（单测语义截断边界，无需走完整 recommend） */
    private String normalize(String s) {
        return (String) ReflectionTestUtils.invokeMethod(service, "normalizeTopic", s);
    }

    @Test
    @DisplayName("归一化 — 短选题原样保留（含折叠内部空白）")
    void normalizeTopic_shortText_unchanged() {
        assertEquals("AI 改变职场", normalize("  AI 改变职场  "));
        assertEquals("程序员 如何提升竞争力", normalize("程序员\n如何提升竞争力"), "换行应折叠为单空格");
    }

    @Test
    @DisplayName("归一化 — 长标题在语义断点截断 + 省略号")
    void normalizeTopic_longText_cutsAtPunctuation() {
        // 20 字内没有标点 → 应在 15-20 区间找断点，无标点则兜底 20 处硬切
        String longNoPunct = "没有标点的一段超长中文标题用来验证兜底硬切";
        String result = normalize(longNoPunct);
        assertEquals(20, result.codePointCount(0, result.length() - 1), "省略号前应为 20 个字符");
        assertTrue(result.endsWith("…"), "超长应加省略号");

        // 含中文标点 → 应保留到最后一个标点
        String withPunct = "2026年AI如何改变职场，以及程序员应该如何应对，深度思考指南";
        String r2 = normalize(withPunct);
        assertTrue(r2.contains("，"), "应保留中文标点断点");
        assertTrue(r2.endsWith("…"));
        assertTrue(r2.codePointCount(0, r2.length() - 1) <= 20, "截断后不超过 20 字");
    }

    @Test
    @DisplayName("归一化 — emoji 不拆分（code point 边界安全）")
    void normalizeTopic_emoji_doesNotSplit() {
        // emoji 是 4 字节 surrogate pair，硬切 UTF-16 会截半 → 应整体保留或整体丢弃，不产生乱码
        String withEmoji = "AI🚀改变职场提升生产力效率的完整指南与深度思考";
        String result = normalize(withEmoji);
        assertFalse(result.contains("�"), "不应出现替换符乱码");
        assertTrue(result.endsWith("…"), "超长应截断");
    }

    @Test
    @DisplayName("归一化 — 三来源长文本都截到 ≤20 字")
    void recommend_longSources_allNormalized() {
        String longHot = "一个特别特别长的平台热门选题话题用于测试归一化逻辑";
        String longHistory = "用户历史中非常长的文章标题讲的是深度思考与长期主义";
        String longAi = "AI生成的一个完整长标题：2026年如何用大模型提升程序员核心竞争力";
        when(articleMapper.countTopicsByPopularity(anyInt()))
                .thenReturn(List.of(hotRow(longHot, 9L)));
        when(ragService.search(anyString(), eq("article"), any(), anyInt()))
                .thenReturn(List.of(new RagService.RagHit("t1", longHistory, "c", 0.9, "article")));
        SkillExecution mockExec = mock(SkillExecution.class);
        when(skillRegistry.createExecution(eq("topic-gen"), any())).thenReturn(mockExec);
        when(mockExec.getPersistedOutput())
                .thenReturn(Map.of("topicOptions", List.of(Map.of("title", longAi))));

        TopicRecommendVO vo = service.recommend(true, normalUser());

        assertFalse(vo.getItems().isEmpty());
        for (TopicRecommendVO.Item item : vo.getItems()) {
            assertTrue(item.getText().codePointCount(0, item.getText().length()) <= 21,
                    "来源 " + item.getSource() + " 截断后不应超过 20 字 + 省略号: " + item.getText());
        }
    }
}
