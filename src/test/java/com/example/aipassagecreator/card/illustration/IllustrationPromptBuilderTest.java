package com.example.aipassagecreator.card.illustration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IllustrationPromptBuilderTest {

    private final IllustrationPromptBuilder builder = new IllustrationPromptBuilder();

    @Test
    void build_includesStyleVisualAndTheme() {
        String prompt = builder.build("如何高效学习", IllustrationCharacterStyle.HEALING);
        assertTrue(prompt.contains("书桌"));
        assertTrue(prompt.contains("暖米黄"));
        assertTrue(prompt.contains("标题区"));
        assertTrue(prompt.contains("无文字"));
    }

    @Test
    void build_differentStyles_differ() {
        String healing = builder.build("标题", IllustrationCharacterStyle.HEALING);
        String doodle = builder.build("标题", IllustrationCharacterStyle.DOODLE);
        assertNotEquals(healing, doodle);
        assertTrue(doodle.contains("马克笔"));
    }

    @Test
    void build_nullTitle_usesDefault() {
        String prompt = builder.build(null, IllustrationCharacterStyle.CUTE);
        assertTrue(prompt.contains("CUTE") || prompt.contains("可爱"));
    }

    @Test
    void resolveCharacterDescription_knowsTopics() {
        assertEquals("书桌", builder.resolveCharacterDescription("高效学习的方法"));
        assertEquals("茶", builder.resolveCharacterDescription("春季养生食谱"));
        assertEquals("城市", builder.resolveCharacterDescription("2025科技趋势"));
    }

    @Test
    void resolveCharacterDescription_unknownTopic_returnsDefault() {
        assertEquals("笑脸", builder.resolveCharacterDescription("随便一段文字"));
    }
}
