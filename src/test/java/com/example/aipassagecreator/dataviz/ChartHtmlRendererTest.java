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

    private Dataset category() {
        return parser.parse("csv", "title,views\nA,100\nB,200\nC,300\n");
    }

    private Dataset twoMeasures() {
        return parser.parse("csv", "price,satisfaction\n10,4.2\n20,3.8\n30,4.6\n");
    }

    private ChartSpec specOf(String chartType, String x, String y) {
        return new ChartSpec(chartType, "glance", "测试图", null, "测试", "元",
                "i1", new ChartSpec.Encoding(x, y, null),
                List.of("i1"), List.of());
    }

    private static int count(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) != -1) { n++; i += needle.length(); }
        return n;
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

    // ── pie ──

    @Test
    void renderChart_pie_emitsOnePathPerSlice() {
        String html = renderer.renderChart(specOf("pie", "title", "views"), category());
        assertEquals(3, count(html, "class=\"pie-slice\""));
        assertTrue(html.contains("<path d=\"M "));
        assertTrue(html.contains("%</text>"));
    }

    @Test
    void renderChart_pie_filtersNonPositiveValues() {
        Dataset ds = parser.parse("csv", "title,views\nA,100\nB,-50\nC,0\n");
        String html = renderer.renderChart(specOf("pie", "title", "views"), ds);
        assertEquals(1, count(html, "class=\"pie-slice\""));
    }

    @Test
    void renderChart_pie_allNonPositive_rendersEmptyState() {
        Dataset ds = parser.parse("csv", "title,views\nA,-1\nB,0\n");
        String html = renderer.renderChart(specOf("pie", "title", "views"), ds);
        assertEquals(0, count(html, "class=\"pie-slice\""));
        assertTrue(html.contains("无有效数值"));
    }

    @Test
    void renderChart_pie_mergesBeyondTenCategories() {
        StringBuilder csv = new StringBuilder("title,views\n");
        for (int i = 1; i <= 12; i++) csv.append("C").append(i).append(',').append(i * 10).append('\n');
        Dataset ds = parser.parse("csv", csv.toString());
        String html = renderer.renderChart(specOf("pie", "title", "views"), ds);
        assertEquals(10, count(html, "class=\"pie-slice\""));
        assertTrue(html.contains("其他"));
    }

    // ── scatter ──

    @Test
    void renderChart_scatter_emitsDotsInsideChartArea() {
        String html = renderer.renderChart(specOf("scatter", "price", "satisfaction"), twoMeasures());
        assertEquals(3, count(html, "class=\"dot\""));
        // 每个 cx/cy 必须落在绘图区内
        for (String attr : new String[]{"cx=\"", "cy=\""}) {
            int i = 0;
            while ((i = html.indexOf(attr, i)) != -1) {
                int end = html.indexOf('"', i + attr.length());
                double v = Double.parseDouble(html.substring(i + attr.length(), end));
                assertTrue(v >= 0 && v <= 640, attr + " 越界: " + v);
                i = end;
            }
        }
    }

    // ── area ──

    @Test
    void renderChart_area_closesPolygonOnBaseline() {
        String html = renderer.renderChart(specOf("area", "month", "revenue"), trend());
        assertTrue(html.contains("class=\"area-fill\""));
        assertTrue(html.contains("class=\"area-line\""));
        assertTrue(html.contains("<polygon"));
        // polygon 的 points 末尾两点应落在同一基线上（y 相等）
        int start = html.indexOf("class=\"area-fill\" points=\"");
        assertTrue(start > -1);
        String pts = html.substring(html.indexOf("points=\"", start) + 8, html.indexOf('"', html.indexOf("points=\"", start) + 8));
        String[] pairs = pts.trim().split("\\s+");
        double lastY = Double.parseDouble(pairs[pairs.length - 1].split(",")[1]);
        double secondLastY = Double.parseDouble(pairs[pairs.length - 2].split(",")[1]);
        assertEquals(secondLastY, lastY, 0.001);
    }

    @Test
    void renderChart_area_singleRowDoesNotDivideByZero() {
        Dataset ds = parser.parse("csv", "month,revenue\n2026-01,100\n");
        String html = renderer.renderChart(specOf("area", "month", "revenue"), ds);
        assertTrue(html.contains("class=\"area-fill\""));
        assertFalse(html.contains("NaN"));
        assertFalse(html.contains("Infinity"));
    }

    @Test
    void renderPage_noScriptAndContainsAll() {
        String page = renderer.renderPage("测试报告", "glance",
                List.of("<div>chart1</div>", "<div>chart2</div>"),
                List.of("检测到 1 个缺失值"));
        assertTrue(page.contains("<!DOCTYPE html"));
        assertTrue(page.contains("测试报告"));
        assertTrue(page.contains("检测到 1 个缺失值"));
        assertFalse(page.toLowerCase().contains("<script"));
        assertFalse(page.contains("cdn."));
    }

    // ── 报告风格 ──

    @Test
    void renderPage_appliesEachStyle() {
        assertTrue(renderer.renderPage("t", "mono", List.of(), List.of()).contains("data-style=\"mono\""));
        assertTrue(renderer.renderPage("t", "editorial", List.of(), List.of()).contains("data-style=\"editorial\""));
        assertTrue(renderer.renderPage("t", "glance", List.of(), List.of()).contains("data-style=\"glance\""));
    }

    @Test
    void renderPage_unknownStyleFallsBackToGlance() {
        assertTrue(renderer.renderPage("t", "neon", List.of(), List.of()).contains("data-style=\"glance\""));
        assertTrue(renderer.renderPage("t", null, List.of(), List.of()).contains("data-style=\"glance\""));
    }

    @Test
    void renderPage_hasNoExternalResources() {
        String page = renderer.renderPage("t", "editorial", List.of(), List.of());
        assertFalse(page.contains("<link"));
        assertFalse(page.contains("@import"));
        assertFalse(page.contains("http://"));
        assertFalse(page.contains("https://"));
    }
}