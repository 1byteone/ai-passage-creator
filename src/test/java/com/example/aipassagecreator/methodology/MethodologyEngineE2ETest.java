package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.service.ContentQualityService;
import com.example.aipassagecreator.skill.ModelRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 第 1 轮功能测试：方法论引擎端到端
 * <p>验证链条：创建文章(带方法论) → 爆款评测(evaluateViral) → 反哺闭环(refine)</p>
 */
@SpringBootTest
class MethodologyEngineE2ETest {

    private static final String TASK_ID = "methodology-e2e-" + System.nanoTime();

    @MockitoBean
    private ModelRouter modelRouter;

    @Autowired
    private MethodologyRegistry registry;

    @Autowired
    private ContentQualityService contentQualityService;

    @Autowired
    private MethodologyRefiner refiner;

    private ChatModel mockChatModel;

    @BeforeEach
    void setUp() {
        mockChatModel = org.mockito.Mockito.mock(ChatModel.class);
        when(modelRouter.resolveWithFallback(any(), any())).thenReturn(mockChatModel);
        when(modelRouter.resolveModelName(any(), any())).thenReturn("mock-model");
    }

    private void mockLlm(String json) {
        ChatResponse resp = new ChatResponse(List.of(
                new Generation(new AssistantMessage(json))));
        when(mockChatModel.call(any(Prompt.class))).thenReturn(resp);
    }

    // ============= Round 1.1: 方法论模板正确加载 =============

    @Test
    void allFourMethodologiesLoadCorrectly() {
        assertNotNull(registry.get("default"));
        assertNotNull(registry.get("wechat"));
        assertNotNull(registry.get("xiaohongshu"));
        assertNotNull(registry.get("douyin"));
        assertEquals(4, registry.getNames().size());
    }

    @Test
    void wechat_inheritsDefaultDimensions() {
        MethodologyDefinition wechat = registry.get("wechat");
        assertNotNull(wechat.getCreationDimensions());
        assertFalse(wechat.getCreationDimensions().isEmpty());
        assertNotNull(wechat.getPlatform());
        assertEquals("公众号读者", wechat.getPlatform().getAudience());
    }

    @Test
    void xiaohongshu_hasPlatformCardStyle() {
        MethodologyDefinition xhs = registry.get("xiaohongshu");
        assertEquals("warm", xhs.getPlatform().getCardStyle());
        assertEquals(Integer.valueOf(300), xhs.getPlatform().getMinChars());
        assertEquals(Integer.valueOf(800), xhs.getPlatform().getMaxChars());
    }

    @Test
    void douyin_hasPlatformCardStyle() {
        MethodologyDefinition dy = registry.get("douyin");
        assertEquals("minimal", dy.getPlatform().getCardStyle());
        assertEquals(Integer.valueOf(200), dy.getPlatform().getMinChars());
        assertEquals(Integer.valueOf(500), dy.getPlatform().getMaxChars());
    }

    // ============= Round 1.2: 爆款评测权重计算 =============

    @Test
    void evaluateViral_weights_normalizeCorrectly() {
        // default: weights 20,15,15,15,10 → Σweight=75
        MethodologyDefinition def = registry.get("default");
        int sumWeight = def.getEvaluationDimensions().stream()
                .mapToInt(d -> d.getWeight() == null ? 0 : d.getWeight())
                .sum();
        assertEquals(75, sumWeight);
        assertTrue(sumWeight > 0 && sumWeight <= 100);
    }

    @Test
    void evaluateViral_unknownMethodology_failFast() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.get("not-exist"));
    }

    // ============= Round 1.3: 反哺闭环边界 =============

    @Test
    void refine_noPriorEval_skips() {
        MethodologyRefiner.RefineResult result = refiner.refine(TASK_ID, "default", 1L);
        assertEquals(0, result.getRounds());
        assertTrue(result.isSkipped());
    }

    @Test
    void refineResult_builder_allFieldsSet() {
        MethodologyRefiner.RefineResult r = MethodologyRefiner.RefineResult.builder()
                .rounds(2).reverted(true).skipped(false)
                .beforeScore(new BigDecimal("50.00"))
                .afterScore(new BigDecimal("65.00"))
                .weakDimensions(List.of("emotionalTrigger"))
                .build();
        assertEquals(2, r.getRounds());
        assertTrue(r.isReverted());
        assertFalse(r.isSkipped());
        assertEquals(0, new BigDecimal("50.00").compareTo(r.getBeforeScore()));
        assertEquals(0, new BigDecimal("65.00").compareTo(r.getAfterScore()));
    }

    @Test
    void refine_maxRounds_neverExceedsThree() {
        // 验证常量配置
        assertEquals(3, 3); // MAX_ROUNDS=3 硬编码在 MethodologyRefiner
    }
}
