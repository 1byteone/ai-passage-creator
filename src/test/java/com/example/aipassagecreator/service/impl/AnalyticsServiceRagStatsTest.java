package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.mapper.ArticleQualityMapper;
import com.example.aipassagecreator.mapper.RagReferenceMapper;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.RagReference;
import com.example.aipassagecreator.model.vo.AnalyticsVO;
import com.example.aipassagecreator.service.RagService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceRagStatsTest {

    private AnalyticsServiceImpl serviceWithMocks(RagReferenceMapper ragRefMapper) {
        AnalyticsServiceImpl s = new AnalyticsServiceImpl();
        ArticleMapper am = mock(ArticleMapper.class);
        when(am.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());
        ReflectionTestUtils.setField(s, "articleMapper", am);
        ReflectionTestUtils.setField(s, "qualityMapper", mock(ArticleQualityMapper.class));
        ReflectionTestUtils.setField(s, "skillExecutionMapper", mock(SkillExecutionMapper.class));
        ReflectionTestUtils.setField(s, "ragReferenceMapper", ragRefMapper);
        ReflectionTestUtils.setField(s, "ragService", mock(RagService.class));
        return s;
    }

    @Test
    @DisplayName("RAG 统计：有引用数据时填充 stage 分布和 avgScore")
    void ragStats_withReferences_populatesFields() {
        RagReferenceMapper mapper = mock(RagReferenceMapper.class);
        when(mapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(
                        new RagReference(null, "t1", "title", "t1", "article", "标题A", 0.85, null),
                        new RagReference(null, "t2", "outline", "t2", "article", "标题B", 0.72, null),
                        new RagReference(null, "t3", "content", "t3", "document", "文档X", 0.91, null)));

        AnalyticsVO vo = serviceWithMocks(mapper).getContentAnalytics();

        assertNotNull(vo.getRagStageDistribution());
        assertEquals(1, (long) vo.getRagStageDistribution().get("title"));
        assertEquals(1, (long) vo.getRagStageDistribution().get("outline"));
        assertEquals(1, (long) vo.getRagStageDistribution().get("content"));
        assertTrue(vo.getRagAvgScore() > 0);
        assertEquals(3L, vo.getRagTotalReferences());
        assertNotNull(vo.getRagRefTypeDistribution());
        assertEquals(2L, (long) vo.getRagRefTypeDistribution().get("article"));
        assertEquals(1L, (long) vo.getRagRefTypeDistribution().get("document"));
    }

    @Test
    @DisplayName("RAG 统计：无引用数据时字段为 null/0")
    void ragStats_noReferences_returnsEmpty() {
        RagReferenceMapper mapper = mock(RagReferenceMapper.class);
        when(mapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        AnalyticsVO vo = serviceWithMocks(mapper).getContentAnalytics();

        assertNull(vo.getRagStageDistribution());
        assertNull(vo.getRagRefTypeDistribution());
        assertEquals(0L, vo.getRagTotalReferences());
        assertNull(vo.getRagAvgScore());
        assertNull(vo.getRagHotQueries());
    }

    @Test
    @DisplayName("RAG 统计：热门查询词按 refTitle 命中次数降序")
    void ragStats_hotQueries_sortedByHitCount() {
        RagReferenceMapper mapper = mock(RagReferenceMapper.class);
        RagReference r1 = new RagReference(null, "t1", "title", "t1", "article", "热门标题", 0.9, null);
        RagReference r2 = new RagReference(null, "t2", "content", "t2", "document", "冷门文档", 0.5, null);
        RagReference r3 = new RagReference(null, "t1", "outline", "t1", "article", "热门标题", 0.8, null);
        when(mapper.selectListByQuery(any(QueryWrapper.class)))
                .thenReturn(List.of(r1, r2, r3));

        AnalyticsVO vo = serviceWithMocks(mapper).getContentAnalytics();
        assertNotNull(vo.getRagHotQueries());
        assertEquals(2, vo.getRagHotQueries().size());
        assertEquals("热门标题", vo.getRagHotQueries().get(0).query());
        assertEquals(2L, vo.getRagHotQueries().get(0).hitCount());
    }

    @Test
    @DisplayName("RAG 统计：个人分析不返回全站引用聚合")
    void ragStats_userAnalyticsDoesNotExposeGlobalStatistics() {
        RagReferenceMapper mapper = mock(RagReferenceMapper.class);
        AnalyticsServiceImpl service = serviceWithMocks(mapper);

        AnalyticsVO vo = service.getUserAnalytics(1001L);

        assertNull(vo.getRagTotalReferences());
        assertNull(vo.getRagAvgScore());
        assertNull(vo.getRagStageDistribution());
        assertNull(vo.getRagRefTypeDistribution());
        assertNull(vo.getRagHotQueries());
    }
}
