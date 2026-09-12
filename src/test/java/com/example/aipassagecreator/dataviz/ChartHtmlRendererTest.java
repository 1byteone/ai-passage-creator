package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChartHtmlRendererTest {

    private final DatasetParser parser = new DatasetParser();
    private final ChartHtmlRenderer renderer = new ChartHtmlRenderer();

    private Dataset trend() {
        return parser.parse("csv", "month,revenue\n2026-01,100\n2026-02,120\n2026-03,150\n");
    }

    @Test
    void renderChart_bar_containsSvgRectsAndEscapedTitle() {
        Dataset ds = trend();
        ChartSpec spec = new ChartSpec("bar", "glance", "收入对比<script>", null, "测试", "元",
                "i1", new ChartSpec.Encoding("month", "revenue", null),
                List.of("i1"), List.of());
        String html = renderer.renderChart(spec, ds);
        assertTrue(html.contains("rect"));
        assertTrue(html.contains("收入对比&lt;script&gt;"));
        assertFalse(html.contains("<script"));
    }

    @Test
    void renderChart_line_containsPolyline() {
        Dataset ds = trend();
        ChartSpec spec = new ChartSpec("line", "mono", "收入趋势", null, "测试", "元",
                "i1", new ChartSpec.Encoding("month", "revenue", null),
                List.of("i1"), List.of());
        String html = renderer.renderChart(spec, ds);
        assertTrue(html.contains("polyline"));
    }

    @Test
    void renderChart_table_containsHtmlTable() {
        Dataset ds = trend();
        ChartSpec spec = new ChartSpec("table", "mono", "明细", null, "测试", null,
                "i1", new ChartSpec.Encoding(null, null, null),
                List.of("i1"), List.of());
        String html = renderer.renderChart(spec, ds);
        assertTrue(html.contains("<table"));
        assertTrue(html.contains("2026-01"));
    }

    @Test
    void renderPage_noScriptAndContainsAll() {
        String page = renderer.renderPage("测试报告",
                List.of("<div>chart1</div>", "<div>chart2</div>"),
                List.of("检测到 1 个缺失值"));
        assertTrue(page.contains("<!DOCTYPE html"));
        assertTrue(page.contains("测试报告"));
        assertTrue(page.contains("检测到 1 个缺失值"));
        assertFalse(page.toLowerCase().contains("<script"));
        assertFalse(page.contains("cdn."));
    }
}