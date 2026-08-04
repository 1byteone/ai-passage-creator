package com.example.aipassagecreator.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * 模型路由单元测试 — 名字映射 + fallback 降级解析
 *
 * <p>验证：阶段/默认模型名解析到正确实例、未知名回落默认、
 * fallback 返回与主模型不同的实例（降级路径真正可触发）。</p>
 */
class ModelRouterTest {

    private final ChatModel agnesChatModel = mock(ChatModel.class);
    private final ChatModel dashscopeChatModel = mock(ChatModel.class);
    private final ModelRouterConfig config = new ModelRouterConfig();
    private ModelRouter router;

    @BeforeEach
    void setUp() {
        router = new ModelRouter(agnesChatModel, dashscopeChatModel, config);
    }

    @Test
    @DisplayName("resolve — agnes 阶段映射到 agnes 实例")
    void resolve_agnes_returnsAgnes() {
        assertSame(agnesChatModel, router.resolve("agnes", null));
    }

    @Test
    @DisplayName("resolve — dashscope 阶段映射到 dashscope 实例")
    void resolve_dashscope_returnsDashscope() {
        assertSame(dashscopeChatModel, router.resolve("dashscope", null));
    }

    @Test
    @DisplayName("resolve — 未知名回落默认模型 (agnes)")
    void resolve_unknownModel_fallsBackToDefault() {
        assertSame(agnesChatModel, router.resolve("unknown-model", null));
    }

    @Test
    @DisplayName("resolve — skill 默认模型优先于全局默认")
    void resolve_usesSkillDefault_whenPhaseModelNull() {
        assertSame(dashscopeChatModel, router.resolve(null, "dashscope"));
    }

    @Test
    @DisplayName("resolveFallback — 返回配置的 fallback (dashscope)，与主模型不同")
    void resolveFallback_returnsDistinctFallbackInstance() {
        ChatModel primary = router.resolve("agnes", null);
        ChatModel fallback = router.resolveFallback();

        assertSame(dashscopeChatModel, fallback);
        assertNotEquals(primary, fallback);
    }

    @Test
    @DisplayName("resolveModelName — 解析实际生效模型名")
    void resolveModelName_resolvesEffectiveName() {
        assertEquals("agnes", router.resolveModelName("agnes", null));
        assertEquals("dashscope", router.resolveModelName(null, "dashscope"));
        // 未知名回落默认
        assertEquals("agnes", router.resolveModelName("unknown", null));
    }
}
