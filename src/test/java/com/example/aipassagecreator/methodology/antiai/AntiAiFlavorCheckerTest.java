package com.example.aipassagecreator.methodology.antiai;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 去AI味检测器测试。
 * <p>覆盖：禁用词检测 / 模板句式 / 过度修饰 / 内容质量规则 / 段落节奏 / 替换映射 / 评分</p>
 */
class AntiAiFlavorCheckerTest {

    // ============= 规则1: AI禁用词 =============

    @Test
    void detect_aiForbiddenWords_found() {
        String text = "首先，总的来说，值得注意的是，AI写作需要避免这些词。";
        List<String> violations = AntiAiFlavorRules.detect(text);
        assertTrue(violations.stream().anyMatch(v -> v.contains("首先")), "应检测到「首先」");
        assertTrue(violations.stream().anyMatch(v -> v.contains("总的来说")), "应检测到「总的来说」");
        assertTrue(violations.stream().anyMatch(v -> v.contains("值得注意的是")), "应检测到「值得注意的是」");
    }

    @Test
    void detect_cleanText_noViolations() {
        String text = "我试过这个方法，真的管用。但说实话，刚开始我也踩过坑。";
        List<String> violations = AntiAiFlavorRules.detect(text);
        assertTrue(violations.isEmpty(), "自然文本不应有违规: " + violations);
    }

    // ============= 规则2: 句式模板 =============

    @Test
    void detect_templatePatterns_found() {
        String text = "在我们的日常生活中，AI技术无处不在。随着科技的进步，越来越多的人开始使用它。";
        List<String> violations = AntiAiFlavorRules.detect(text);
        assertTrue(violations.stream().anyMatch(v -> v.contains("在我们的日常生活中")),
                "应检测到「在我们的日常生活中」");
        assertTrue(violations.stream().anyMatch(v -> v.contains("随着科技的")),
                "应检测到「随着科技的」");
    }

    // ============= 规则3: 过度修饰 =============

    @Test
    void detect_overusedAdverbs_found() {
        String text = "这个产品非常优秀，显然比竞品更加出色，毫无疑问是行业领先。";
        List<String> violations = AntiAiFlavorRules.detect(text);
        assertTrue(violations.stream().anyMatch(v -> v.contains("非常")), "应检测到「非常」");
        assertTrue(violations.stream().anyMatch(v -> v.contains("显然")), "应检测到「显然」");
        assertTrue(violations.stream().anyMatch(v -> v.contains("毫无疑问")), "应检测到「毫无疑问」");
    }

    @Test
    void detect_humanizerQualityPatterns_found() {
        String text = "我看过行业报告显示的结论，专家认为这个方案很强大。未来看起来光明。"
                + "不仅功能完整，而且部署很快。——补充——再补充——最后说明。😀";
        List<String> violations = AntiAiFlavorRules.detect(text);
        assertTrue(violations.stream().anyMatch(v -> v.contains("模糊归因")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("过度修饰")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("空泛结论")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("否定式排比")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("破折号过度")));
        assertTrue(violations.stream().anyMatch(v -> v.contains("表情符号")));
    }

    // ============= 规则4: 体裁适配 =============

    @Test
    void detect_formalText_withoutFirstPerson_isNotPenalized() {
        String text = "本文将从三个维度分析这个问题。作者认为这是一个值得关注的方向。";
        List<String> violations = AntiAiFlavorRules.detect(text);
        assertTrue(violations.stream().noneMatch(v -> v.contains("缺个人视角")),
                "正式文本不应因缺少「我」而被扣分");
    }

    @Test
    void detect_withPersonalPerspective_isSupported() {
        String text = "我试过很多方法，最后发现这个最管用。我觉得关键是坚持。";
        List<String> violations = AntiAiFlavorRules.detect(text);
        assertTrue(violations.stream().noneMatch(v -> v.contains("缺个人视角")),
                "使用第一人称的文章也不应触发个人视角规则");
    }

    // ============= 规则5: 段落节奏 =============

    @Test
    void detect_uniformParagraphs_warns() {
        String text = "第一段内容大概有这么多字。\n\n第二段内容也差不多字数。\n\n第三段同样保持均衡。";
        List<String> violations = AntiAiFlavorRules.detect(text);
        // 段落字数差异 < 30% 时会触发
        assertTrue(violations.stream().anyMatch(v -> v.contains("段落节奏")),
                "均匀段落应触发节奏警告");
    }

    // ============= Checker 评分 =============

    @Test
    void checker_heavilyAiFlavored_fails() {
        String text = "首先，总的来说，值得注意的是，这个方案非常优秀。显然，它对行业产生了深远的影响。"
                + "在我们的日常生活中，AI技术扮演着越来越重要的角色。"
                + "从某种意义上说，它改变了我们的生活方式。";
        var report = AntiAiFlavorChecker.check(text);
        assertFalse(report.passed(), "AI味过重应判定不通过");
        assertTrue(report.score() < 60, "得分应低于 60: " + report.score());
    }

    @Test
    void checker_naturalText_passes() {
        String text = "我跟你说，这事儿我试过。刚开始我也觉得不行，但后来发现其实没那么难。"
                + "关键是别想太多，直接开干。";
        var report = AntiAiFlavorChecker.check(text);
        assertTrue(report.passed(), "自然文本应通过检测");
        assertTrue(report.score() >= 80, "自然文本得分应 ≥ 80: " + report.score());
    }

    @Test
    void checker_usesConfiguredPassThreshold() {
        String text = "我首先记录了这次测试结果。";
        var passesAtScore = AntiAiFlavorChecker.check(text, 90);
        var failsAboveScore = AntiAiFlavorChecker.check(text, 91);

        assertEquals(90, passesAtScore.score());
        assertTrue(passesAtScore.passed(), "分数等于阈值时应通过");
        assertFalse(failsAboveScore.passed(), "分数低于阈值时应不通过");
    }

    // ============= 替换映射 =============

    @Test
    void replacementMap_containsKeyEntries() {
        assertTrue(AntiAiFlavorRules.REPLACEMENT_MAP.containsKey("因此"));
        assertTrue(AntiAiFlavorRules.REPLACEMENT_MAP.containsKey("例如"));
        assertTrue(AntiAiFlavorRules.REPLACEMENT_MAP.containsKey("然而"));
        assertTrue(AntiAiFlavorRules.REPLACEMENT_MAP.containsKey("获得"));
        assertTrue(AntiAiFlavorRules.REPLACEMENT_MAP.containsKey("此外"));
        assertTrue(AntiAiFlavorRules.REPLACEMENT_MAP.size() >= 50, "替换映射应 ≥ 50 条");
    }

    // ============= 空/边界 =============

    @Test
    void detect_nullText_returnsEmpty() {
        List<String> violations = AntiAiFlavorRules.detect(null);
        assertTrue(violations.isEmpty());
    }

    @Test
    void detect_blankText_returnsEmpty() {
        List<String> violations = AntiAiFlavorRules.detect("   ");
        assertTrue(violations.isEmpty());
    }

    @Test
    void detect_shortText_noViolations() {
        List<String> violations = AntiAiFlavorRules.detect("好的，我明白了。");
        assertTrue(violations.isEmpty());
    }
}
