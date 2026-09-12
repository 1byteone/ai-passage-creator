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

    private ChartSpec lineSpec(String x, String y) {
        return new ChartSpec("line", "mono", "收入趋势", null, "测试", "元",
                "i1", new ChartSpec.Encoding(x, y, null),
                List.of("i1"), List.of());
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
