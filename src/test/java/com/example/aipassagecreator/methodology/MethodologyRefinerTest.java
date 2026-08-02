package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.model.po.ArticleVersion;
import com.example.aipassagecreator.service.ArticleRewriteService;
import com.example.aipassagecreator.service.ContentQualityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 爆款反哺闭环 MethodologyRefiner 测试
 * <p>在真实 Spring 上下文（真实 MethodologyRegistry + 真实 YAML）上，
 * mock 掉两个外部服务，聚焦闭环调度逻辑、低分维度排序与版本回退语义。</p>
 */
@SpringBootTest
class MethodologyRefinerTest {

    private static final String TASK_ID = "refine-test-task";

    @Autowired
    private MethodologyRefiner refiner;

    @Autowired
    private MethodologyRegistry registry;

    @MockitoBean
    private ContentQualityService contentQualityService;

    @MockitoBean
    private ArticleRewriteService articleRewriteService;

    /** refine_maxRounds_capsAtThree 中记录 evaluateViral 调用次数，返回递增分数 */
    private int invocationCount;

    @BeforeEach
    void setUp() {
        invocationCount = 0;
        when(contentQualityService.getLatestViral(TASK_ID)).thenReturn(null);
    }

    /** 纯 POJO 断言：brief 最小 builder 测试 */
    @Test
    void refineResult_builderWorks() {
        MethodologyRefiner.RefineResult result = MethodologyRefiner.RefineResult.builder()
                .rounds(1)
                .reverted(false)
                .build();
        assertEquals(1, result.getRounds());
        assertFalse(result.isReverted());
    }

    /** findWeakDimensions：低分维度按分数升序（最弱在前），top-2 才是真正的"最弱"维度 */
    @Test
    void findWeakDimensions_sortsWeakestFirst() {
        ArticleQuality q = quality("{\"emotionalTrigger\":55,\"goldenSentence\":40,\"interactionHook\":80,\"persuasion\":75,\"titleStrategy\":70}");
        MethodologyDefinition def = registry.get("default");
        @SuppressWarnings("unchecked")
        List<String> weak = (List<String>) ReflectionTestUtils.invokeMethod(
                refiner, "findWeakDimensions", q, def);
        assertNotNull(weak);
        // goldenSentence(40) 应排在 emotionalTrigger(55) 之前
        assertEquals(List.of("goldenSentence", "emotionalTrigger"), weak);
    }

    @Test
    void findWeakDimensions_emptyWhenNoneBelowThreshold() {
        ArticleQuality q = quality("{\"emotionalTrigger\":80,\"goldenSentence\":75,\"interactionHook\":85,\"persuasion\":90,\"titleStrategy\":70}");
        MethodologyDefinition def = registry.get("default");
        @SuppressWarnings("unchecked")
        List<String> weak = (List<String>) ReflectionTestUtils.invokeMethod(
                refiner, "findWeakDimensions", q, def);
        assertNotNull(weak);
        assertTrue(weak.isEmpty());
    }

    /** buildRefineInstruction：低分维度 key → 对应创作维度 guidance */
    @Test
    void buildRefineInstruction_containsGuidance() {
        MethodologyDefinition def = registry.get("default");
        String instruction = (String) ReflectionTestUtils.invokeMethod(
                refiner, "buildRefineInstruction", def, List.of("goldenSentence", "emotionalTrigger"));
        assertNotNull(instruction);
        assertTrue(instruction.contains("金句"));
        assertTrue(instruction.contains("情绪触发点"));
        assertTrue(instruction.contains("可独立转发"));
    }

    /** 无爆款评测结果 → 跳过，不触发改写 */
    @Test
    void refine_noEval_skips() {
        MethodologyRefiner.RefineResult result = refiner.refine(TASK_ID, "default", 1L);
        assertEquals(0, result.getRounds());
        assertTrue(result.isSkipped());
        verify(articleRewriteService, never()).rewriteSection(any(), any(), any(), any());
    }

    /** 评测达标且无低分维度 → 跳过 */
    @Test
    void refine_qualifies_skips() {
        ArticleQuality initial = quality("{\"emotionalTrigger\":85,\"goldenSentence\":80,\"interactionHook\":88,\"persuasion\":90,\"titleStrategy\":82}");
        initial.setViralScore(new BigDecimal("85.00"));
        initial.setVersionNo(1);
        when(contentQualityService.getLatestViral(TASK_ID)).thenReturn(initial);

        MethodologyRefiner.RefineResult result = refiner.refine(TASK_ID, "default", 1L);
        assertEquals(0, result.getRounds());
        assertTrue(result.isSkipped());
        verify(articleRewriteService, never()).rewriteSection(any(), any(), any(), any());
    }

    /** viralScore ≥ 70 但仍有弱维 → 应继续执行反哺，而非跳过 */
    @Test
    void refine_doesNotSkipWhenWeakDimsExistDespiteHighViralScore() {
        ArticleQuality quality = quality("{\"emotionalTrigger\":70,\"goldenSentence\":40,\"interactionHook\":85,\"persuasion\":80,\"titleStrategy\":75}");
        quality.setViralScore(new BigDecimal("72.00"));
        quality.setVersionNo(1);
        when(contentQualityService.getLatestViral(TASK_ID)).thenReturn(quality);
        // evaluateViral 返回不变（平局，触发回退终止而非跳过）
        when(contentQualityService.evaluateViral(eq(TASK_ID), eq("default"), eq(1L))).thenReturn(quality);
        when(articleRewriteService.rewriteSection(eq(TASK_ID), anyString(), isNull(), eq(1L)))
                .thenReturn(ArticleVersion.builder().taskId(TASK_ID).versionNo(1).build());

        MethodologyRefiner.RefineResult result = refiner.refine(TASK_ID, "default", 1L);
        assertFalse(result.isSkipped(), "有弱维时应执行反哺而非跳过");
        assertTrue(result.getRounds() > 0);
    }

    /** 单轮改写后复评提升且无低分维度 → 1 轮收敛 */
    @Test
    void refine_improves_oneRound() {
        ArticleQuality initial = quality("{\"emotionalTrigger\":40,\"goldenSentence\":55,\"interactionHook\":80,\"persuasion\":75,\"titleStrategy\":70}");
        initial.setViralScore(new BigDecimal("50.00"));
        initial.setVersionNo(1);
        when(contentQualityService.getLatestViral(TASK_ID)).thenReturn(initial);

        ArticleQuality reEval = quality("{\"emotionalTrigger\":80,\"goldenSentence\":75,\"interactionHook\":85,\"persuasion\":85,\"titleStrategy\":80}");
        reEval.setViralScore(new BigDecimal("82.00"));
        reEval.setVersionNo(2);
        when(contentQualityService.evaluateViral(eq(TASK_ID), eq("default"), eq(1L))).thenReturn(reEval);
        when(articleRewriteService.rewriteSection(eq(TASK_ID), anyString(), isNull(), eq(1L)))
                .thenReturn(ArticleVersion.builder().taskId(TASK_ID).versionNo(1).build());

        MethodologyRefiner.RefineResult result = refiner.refine(TASK_ID, "default", 1L);
        assertEquals(1, result.getRounds());
        assertFalse(result.isReverted());
        assertFalse(result.isSkipped());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getBeforeScore()));
        assertEquals(0, new BigDecimal("82.00").compareTo(result.getAfterScore()));
        verify(articleRewriteService, times(1)).rewriteSection(eq(TASK_ID), anyString(), isNull(), eq(1L));
        verify(articleRewriteService, never()).revertTo(any(), anyInt(), any());
    }

    /** 复评无提升 → 回退到改写前的版本（本轮创建了版本 3 → 回退到 2） */
    @Test
    void refine_noImprovement_reverts() {
        ArticleQuality initial = quality("{\"emotionalTrigger\":40,\"goldenSentence\":55,\"interactionHook\":80,\"persuasion\":75,\"titleStrategy\":70}");
        initial.setViralScore(new BigDecimal("50.00"));
        initial.setVersionNo(1);
        when(contentQualityService.getLatestViral(TASK_ID)).thenReturn(initial);

        ArticleQuality reEval = quality("{\"emotionalTrigger\":30,\"goldenSentence\":40,\"interactionHook\":70,\"persuasion\":65,\"titleStrategy\":60}");
        reEval.setViralScore(new BigDecimal("40.00"));
        reEval.setVersionNo(2);
        when(contentQualityService.evaluateViral(eq(TASK_ID), eq("default"), eq(1L))).thenReturn(reEval);
        when(articleRewriteService.rewriteSection(eq(TASK_ID), anyString(), isNull(), eq(1L)))
                .thenReturn(ArticleVersion.builder().taskId(TASK_ID).versionNo(3).build());

        MethodologyRefiner.RefineResult result = refiner.refine(TASK_ID, "default", 1L);
        assertEquals(1, result.getRounds());
        assertTrue(result.isReverted());
        assertFalse(result.isSkipped());
        // #41: 回退后内容回到改写前的更优分数，afterScore 报 before（50.00）而非中间坏分（40.00）
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getAfterScore()));
        verify(articleRewriteService).revertTo(TASK_ID, 2, 1L);
        // B2-4: 回退目标内容（改写前）的评测必须恢复，否则 getLatestViral 停在坏分上
        verify(contentQualityService).restoreViral(TASK_ID, initial);
    }

    /** 无提升但本轮改写版本为 1（无前驱版本可回退）→ 标记 reverted 但跳过 revertTo */
    @Test
    void refine_noImprovement_noPriorVersion_marksReverted() {
        ArticleQuality initial = quality("{\"emotionalTrigger\":40,\"goldenSentence\":55,\"interactionHook\":80,\"persuasion\":75,\"titleStrategy\":70}");
        initial.setViralScore(new BigDecimal("50.00"));
        initial.setVersionNo(1);
        when(contentQualityService.getLatestViral(TASK_ID)).thenReturn(initial);

        ArticleQuality reEval = quality("{\"emotionalTrigger\":30,\"goldenSentence\":40,\"interactionHook\":70,\"persuasion\":65,\"titleStrategy\":60}");
        reEval.setViralScore(new BigDecimal("40.00"));
        when(contentQualityService.evaluateViral(eq(TASK_ID), eq("default"), eq(1L))).thenReturn(reEval);
        when(articleRewriteService.rewriteSection(eq(TASK_ID), anyString(), isNull(), eq(1L)))
                .thenReturn(ArticleVersion.builder().taskId(TASK_ID).versionNo(1).build());

        MethodologyRefiner.RefineResult result = refiner.refine(TASK_ID, "default", 1L);
        assertTrue(result.isReverted());
        verify(articleRewriteService, never()).revertTo(any(), anyInt(), any());
        // 无前驱版本可回退时不恢复评分（没有需要覆盖的"已回退内容"分数）
        verify(contentQualityService, never()).restoreViral(any(), any());
    }

    /**
     * 每轮都提升（viralScore 逐轮递增）但低分维度持续不达标 → 最多 3 轮封顶。
     * 注意：evaluateViral 按调用次数返回递增分数，避免 <#40 平局回退> 提前终止。
     */
    @Test
    void refine_maxRounds_capsAtThree() {
        ArticleQuality initial = quality("{\"emotionalTrigger\":40,\"goldenSentence\":55,\"interactionHook\":80,\"persuasion\":75,\"titleStrategy\":70}");
        initial.setViralScore(new BigDecimal("50.00"));
        initial.setVersionNo(1);
        when(contentQualityService.getLatestViral(TASK_ID)).thenReturn(initial);

        // 每轮 viralScore 递增：50 → 60 → 70 → 80，均大于前值，永不触发平局回退
        List<BigDecimal> scores = List.of(
                new BigDecimal("60.00"), new BigDecimal("70.00"), new BigDecimal("80.00"));
        when(contentQualityService.evaluateViral(eq(TASK_ID), eq("default"), eq(1L)))
                .thenAnswer(inv -> {
                    int idx = (int) Math.min(scores.size() - 1, invocationCount++);
                    ArticleQuality improved = quality("{\"emotionalTrigger\":70,\"goldenSentence\":40,\"interactionHook\":85,\"persuasion\":80,\"titleStrategy\":75}");
                    improved.setViralScore(scores.get(idx));
                    return improved;
                });
        when(articleRewriteService.rewriteSection(eq(TASK_ID), anyString(), isNull(), eq(1L)))
                .thenReturn(ArticleVersion.builder().taskId(TASK_ID).versionNo(1).build());

        MethodologyRefiner.RefineResult result = refiner.refine(TASK_ID, "default", 1L);
        assertEquals(3, result.getRounds());
        assertFalse(result.isReverted());
        assertFalse(result.isSkipped());
        verify(articleRewriteService, times(3)).rewriteSection(eq(TASK_ID), anyString(), isNull(), eq(1L));
        verify(articleRewriteService, never()).revertTo(any(), anyInt(), any());
    }

    private ArticleQuality quality(String viralScoresJson) {
        return ArticleQuality.builder()
                .taskId(TASK_ID)
                .scoreType("VIRAL")
                .viralScores(viralScoresJson)
                .build();
    }
}
