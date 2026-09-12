package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChartSpecValidatorTest {

    private final DatasetParser parser = new DatasetParser();
    private final DatasetValidator validator = new DatasetValidator();
    private final ChartSpecValidator validator2 = new ChartSpecValidator();

    private record Ctx(Dataset ds, DataQualityReport profile) {}

    private Ctx trendCtx() {
        Dataset ds = parser.parse("csv", "month,revenue\n2026-01,100\n2026-02,120\n");
        return new Ctx(ds, validator.validate(ds));
    }

    private Ctx categoryCtx() {
        Dataset ds = parser.parse("csv", "title,views\nA,100\nB,200\n");
        return new Ctx(ds, validator.validate(ds));
    }

    /** 两个数值列，供散点图（x/y 均为 measure）使用 */
    private Ctx measureCtx() {
        Dataset ds = parser.parse("csv", "price,satisfaction\n10,4.2\n20,3.8\n30,4.6\n");
        return new Ctx(ds, validator.validate(ds));
    }

    private ChartSpec lineSpec(String x, String y) {
        return new ChartSpec("line", "mono", "收入趋势", null, "测试", "元",
                "i1", new ChartSpec.Encoding(x, y, null),
                List.of("i1"), List.of());
    }

    private ChartSpec specOf(String chartType, String x, String y) {
        return new ChartSpec(chartType, "glance", "测试图", null, "测试", "元",
                "i1", new ChartSpec.Encoding(x, y, null),
                List.of("i1"), List.of());
    }

    // ── pie ──

    @Test
    void validate_pieOnCategoryData_passes() {
        var c = categoryCtx();
        assertDoesNotThrow(() -> validator2.validate(specOf("pie", "title", "views"), c.ds(), c.profile()));
    }

    @Test
    void validate_pieWithTimeX_blocked() {
        var c = trendCtx();
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(specOf("pie", "month", "revenue"), c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("分类字段"));
    }

    @Test
    void validate_pieWithoutEncoding_blocked() {
        var c = categoryCtx();
        ChartSpec spec = new ChartSpec("pie", "glance", "构成", null, "测试", null,
                "i1", null, List.of("i1"), List.of());
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("数据映射"));
    }

    @Test
    void validate_pieWithNonMeasureY_blocked() {
        // x 取分类列以绕过 x 规则，专门验证 y 必须是数值
        var c = categoryCtx();
        ChartSpec spec = new ChartSpec("pie", "glance", "构成", null, "测试", null,
                "i1", new ChartSpec.Encoding("title", "title", null),
                List.of("i1"), List.of());
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("纵轴"));
    }

    // ── scatter ──

    @Test
    void validate_scatterOnTwoMeasures_passes() {
        var c = measureCtx();
        assertDoesNotThrow(() -> validator2.validate(specOf("scatter", "price", "satisfaction"), c.ds(), c.profile()));
    }

    @Test
    void validate_scatterWithCategoryX_blocked() {
        var c = categoryCtx();
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(specOf("scatter", "title", "views"), c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("数值字段"));
    }

    // ── area ──

    @Test
    void validate_areaOnTrendData_passes() {
        var c = trendCtx();
        assertDoesNotThrow(() -> validator2.validate(specOf("area", "month", "revenue"), c.ds(), c.profile()));
    }

    @Test
    void validate_areaWithCategoryX_blocked() {
        var c = categoryCtx();
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(specOf("area", "title", "views"), c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("时间字段"));
    }

    @Test
    void validate_lineOnTrendData_passes() {
        var c = trendCtx();
        ChartSpec spec = lineSpec("month", "revenue");
        assertDoesNotThrow(() -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_barOnCategoryData_passes() {
        var c = categoryCtx();
        ChartSpec spec = new ChartSpec("bar", "glance", "阅读量排名", null, "测试", "次",
                "i1", new ChartSpec.Encoding("title", "views", null),
                List.of("i1"), List.of());
        assertDoesNotThrow(() -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_unknownChartType_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("sankey", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("month", "revenue", null),
                List.of("i1"), List.of());
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("图表类型"));
    }

    @Test
    void validate_lineWithoutTimeX_blocked() {
        var c = categoryCtx();
        ChartSpec spec = new ChartSpec("line", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("title", "views", null),
                List.of("i1"), List.of());
        assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_yFieldNotMeasure_blocked() {
        // x 必须取分类列，否则会先被"柱状图横轴必须为分类字段"拦下，测不到 y 规则
        var c = categoryCtx();
        ChartSpec spec = new ChartSpec("bar", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("title", "title", null),
                List.of("i1"), List.of());
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("纵轴"));
    }

    @Test
    void validate_nullChartType_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec(null, "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("month", "revenue", null),
                List.of("i1"), List.of());
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("图表类型"));
    }

    @Test
    void validate_unknownStyle_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("line", "neon", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("month", "revenue", null),
                List.of("i1"), List.of());
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("视觉风格"));
    }

    @Test
    void validate_titleTooLong_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("line", "mono", "长".repeat(81), null, "s", null,
                "i1", new ChartSpec.Encoding("month", "revenue", null),
                List.of("i1"), List.of());
        assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_nullEvidence_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("line", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("month", "revenue", null),
                null, List.of());
        assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_unknownField_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("line", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("nope", "revenue", null),
                List.of("i1"), List.of());
        assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_emptyEvidence_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("line", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("month", "revenue", null),
                List.of(), List.of());
        assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_tableWithoutEncoding_passes() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("table", "mono", "明细", null, "s", null,
                "i1", new ChartSpec.Encoding(null, null, null),
                List.of("i1"), List.of());
        assertDoesNotThrow(() -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_tableWithKnownEncoding_passes() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("table", "mono", "明细", null, "s", null,
                "i1", new ChartSpec.Encoding("month", "revenue", null),
                List.of("i1"), List.of());
        assertDoesNotThrow(() -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_tableWithUnknownMappedField_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("table", "mono", "明细", null, "s", null,
                "i1", new ChartSpec.Encoding("nope", "revenue", null),
                List.of("i1"), List.of());
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("字段不存在"));
    }
}
