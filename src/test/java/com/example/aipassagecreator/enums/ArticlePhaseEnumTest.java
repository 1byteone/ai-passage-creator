package com.example.aipassagecreator.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文章阶段枚举测试 — 验证状态机合法/非法转换。
 */
class ArticlePhaseEnumTest {

    @ParameterizedTest(name = "{0} -> {1} 应为合法转换")
    @CsvSource({
        "PENDING, TITLE_GENERATING",
        "TITLE_GENERATING, TITLE_SELECTING",
        "TITLE_SELECTING, OUTLINE_GENERATING",
        "OUTLINE_GENERATING, OUTLINE_EDITING",
        "OUTLINE_EDITING, CONTENT_GENERATING",
    })
    @DisplayName("合法阶段转换被允许")
    void canTransitionTo_validTransitions_allowed(ArticlePhaseEnum from, ArticlePhaseEnum to) {
        assertTrue(from.canTransitionTo(to), from + " 应能转换到 " + to);
    }

    @ParameterizedTest(name = "{0} -> {1} 应为非法转换")
    @CsvSource({
        "PENDING, OUTLINE_EDITING",
        "PENDING, CONTENT_GENERATING",
        "TITLE_SELECTING, CONTENT_GENERATING",
        "OUTLINE_EDITING, TITLE_GENERATING",
        "CONTENT_GENERATING, TITLE_SELECTING",
        "TITLE_GENERATING, PENDING",
    })
    @DisplayName("非法/回退阶段转换被拒绝")
    void canTransitionTo_invalidTransitions_denied(ArticlePhaseEnum from, ArticlePhaseEnum to) {
        assertFalse(from.canTransitionTo(to), from + " 不应允许转换到 " + to);
    }

    @Test
    @DisplayName("CONTENT_GENERATING 为最终阶段，不再转换")
    void canTransitionTo_finalPhase_deniesAll() {
        ArticlePhaseEnum finalPhase = ArticlePhaseEnum.CONTENT_GENERATING;
        for (ArticlePhaseEnum candidate : ArticlePhaseEnum.values()) {
            assertFalse(finalPhase.canTransitionTo(candidate), "最终阶段不应转换到 " + candidate);
        }
    }

    @Test
    @DisplayName("getByValue 能解析字符串并容忍 null/未知值")
    void getByValue_parsesString_handlesNull() {
        assertTrue(ArticlePhaseEnum.getByValue("TITLE_SELECTING") == ArticlePhaseEnum.TITLE_SELECTING);
        assertTrue(ArticlePhaseEnum.getByValue("unknown") == null);
        assertTrue(ArticlePhaseEnum.getByValue(null) == null);
    }

    @Test
    @DisplayName("null 目标阶段被拒绝")
    void canTransitionTo_nullTarget_denied() {
        assertFalse(ArticlePhaseEnum.PENDING.canTransitionTo(null));
    }
}
