package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.ArticleMapper;
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
}
