package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全链路集成：真实 CSV → 画像 → 六种图型 Spec 校验 → 渲染 → 整页报告。
 * <p>不经 mock，也不依赖 AI（Chart Spec 在测试内构造），用于守住各组件串起来后的契约。
 */
class DataVizPipelineIntegrationTest {

    private static final String CSV = """
            month,channel,revenue,orders,conversion_rate
            2026-01,自然流量,164000,820,0.12
            2026-02,自然流量,172000,860,0.13
            2026-03,自然流量,191000,910,0.14
            2026-01,付费投放,98000,420,0.21
            2026-02,付费投放,112000,455,0.23
            2026-03,付费投放,131000,498,0.25
            """;

    private final DatasetParser parser = new DatasetParser();
    private final DatasetValidator validator = new DatasetValidator();
    private final ChartSpecValidator specValidator = new ChartSpecValidator();
    private final ChartHtmlRenderer renderer = new ChartHtmlRenderer();

    private static ChartSpec chart(String type, String style, String title, String x, String y) {
        return new ChartSpec(type, style, title, null, "用户提供数据", "元",
                "i1", new ChartSpec.Encoding(x, y, null), List.of("i1"), List.of());
    }

    private static int countOf(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) != -1) { n++; i += needle.length(); }
        return n;
    }

    @Test
    @DisplayName("真实 CSV 经全链路产出可渲染的六图型报告")
    void fullCsvPipeline_producesRenderableReport() {
        Dataset ds = parser.parse("csv", CSV);
        DataQualityReport profile = validator.validate(ds);

        // 字段语义推断必须正确，否则下游图型规则会全线失配
        assertEquals("time", fieldSemantic(profile, "month"));
        assertEquals("category", fieldSemantic(profile, "channel"));
        assertEquals("measure", fieldSemantic(profile, "revenue"));
        assertEquals("measure", fieldSemantic(profile, "conversion_rate"));

        List<ChartSpec> specs = List.of(
                chart("line", "editorial", "收入趋势", "month", "revenue"),
                chart("area", "glance", "订单累计", "month", "orders"),
                chart("bar", "glance", "渠道收入排名", "channel", "revenue"),
                chart("pie", "mono", "渠道构成", "channel", "revenue"),
                chart("scatter", "glance", "订单与转化率关系", "orders", "conversion_rate")
        );

        List<String> fragments = new ArrayList<>();
        for (ChartSpec spec : specs) {
            // 每种图型的横轴语义规则都必须放行真实数据
            assertDoesNotThrow(() -> specValidator.validate(spec, ds, profile),
                    "图型 " + spec.chartType() + " 应通过校验");
            String fragment = renderer.renderChart(spec, ds);
            assertFalse(fragment.isBlank());
            fragments.add(fragment);
        }
        fragments.add(renderer.renderChart(new ChartSpec("table", "mono", "明细", null,
                "用户提供数据", null, "i1", new ChartSpec.Encoding(null, null, null),
                List.of("i1"), List.of()), ds));

        String html = renderer.renderPage("渠道经营分析", "editorial", fragments, profile.warnings());

        assertEquals(6, countOf(html, "class=\"chart-card"));
        assertTrue(html.contains("class=\"pie-slice\""));
        assertTrue(html.contains("class=\"dot\""));
        assertTrue(html.contains("class=\"area-fill\""));
        assertTrue(html.contains("polyline"));
        assertTrue(html.contains("<table"));
        assertTrue(html.contains("data-style=\"editorial\""));
        // 单文件、无脚本、无外部依赖
        assertFalse(html.toLowerCase().contains("<script"));
        assertFalse(html.contains("<link"));
        assertFalse(html.contains("http://"));
        assertFalse(html.contains("https://"));
    }

    @Test
    @DisplayName("风格非法时报告兜底 glance，不因风格值崩掉整份报告")
    void unknownStyle_fallsBackToGlance() {
        Dataset ds = parser.parse("csv", CSV);
        DataQualityReport profile = validator.validate(ds);
        String card = renderer.renderChart(chart("bar", "glance", "排名", "channel", "revenue"), ds);

        String page = renderer.renderPage("t", "not-a-style", List.of(card), List.of());
        assertTrue(page.contains("data-style=\"glance\""));
        assertTrue(page.contains("class=\"chart-card"));
    }

    private String fieldSemantic(DataQualityReport profile, String name) {
        return profile.fields().stream()
                .filter(f -> f.name().equals(name))
                .map(FieldProfile::semantic)
                .findFirst()
                .orElseThrow(() -> new AssertionError("字段缺失: " + name));
    }
}
