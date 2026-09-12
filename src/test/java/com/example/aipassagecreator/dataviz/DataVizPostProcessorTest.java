package com.example.aipassagecreator.dataviz;

import com.example.aipassagecreator.card.CardRenderPipeline;
import com.example.aipassagecreator.card.model.PageResult;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * dataviz 收尾产物：HTML 落盘 + PNG 安全降级。
 * <p>PNG 分支用 mock 渲染管线覆盖，测试不依赖 Chromium 存在。
 */
class DataVizPostProcessorTest {

    @TempDir
    Path tempDir;

    // ── 报告风格取值优先级 ──

    private ChartSpec styledSpec(String style) {
        return new ChartSpec("bar", style, "t", null, "s", null,
                "i1", new ChartSpec.Encoding("title", "views", null), List.of("i1"), List.of());
    }

    @Test
    @DisplayName("resolveStyle 优先取 Skill INPUT 变量 style")
    void resolveStyle_prefersInputVariable() {
        assertEquals("editorial",
                DataVizPostProcessor.resolveStyle(Map.of("style", "editorial"), List.of(styledSpec("glance"))));
    }

    @Test
    @DisplayName("resolveStyle 无 INPUT 变量时取首张图表风格")
    void resolveStyle_fallsBackToFirstSpec() {
        assertEquals("mono", DataVizPostProcessor.resolveStyle(Map.of(), List.of(styledSpec("mono"))));
    }

    @Test
    @DisplayName("resolveStyle 都缺失时兜底 glance")
    void resolveStyle_defaultsToGlance() {
        assertEquals("glance", DataVizPostProcessor.resolveStyle(Map.of(), List.of()));
        assertEquals("glance", DataVizPostProcessor.resolveStyle(Map.of("style", "  "), List.of()));
    }

    private DataVizPostProcessor processor(CardRenderPipeline pipeline) {
        return new DataVizPostProcessor(
                new DatasetParser(), new DatasetValidator(),
                new ChartSpecValidator(), new ChartHtmlRenderer(),
                pipeline, new DataVizStorageService());
    }

    private Map<String, Object> barOutput() {
        Map<String, Object> chart = Map.of(
                "chartType", "bar",
                "style", "glance",
                "title", "阅读量排名",
                "source", "测试",
                "insightId", "i1",
                "evidence", List.of("i1"),
                "encoding", Map.of("x", "title", "y", "views"));
        return Map.of(
                "rawData", "[{\"title\":\"A\",\"views\":100},{\"title\":\"B\",\"views\":200}]",
                "dataFormat", "json",
                "chartSpecs", Map.of("charts", List.of(chart)));
    }

    private static CardRenderPipeline pipeline(boolean healthy, PageResult... results) {
        CardRenderPipeline mock = mock(CardRenderPipeline.class);
        when(mock.isHealthy()).thenReturn(healthy);
        when(mock.render(any(), any())).thenReturn(List.of(results));
        return mock;
    }

    @Test
    @DisplayName("合法规格 → 写出 report.html（含标题、无 script）")
    void process_validSpecs_writesHtml() throws Exception {
        processor(null).process(tempDir, "exec-1", barOutput());

        Path html = tempDir.resolve("dataviz/exec-1/report.html");
        assertTrue(Files.exists(html));
        String content = Files.readString(html);
        assertTrue(content.contains("阅读量排名"));
        assertFalse(content.toLowerCase().contains("<script"));
    }

    @Test
    @DisplayName("渲染引擎健康 → 同一份 HTML 产出 report.png")
    void process_healthyPipeline_writesPng() throws Exception {
        // 渲染引擎不可用时 PNG 只是可选产物；健康时必须真的落盘
        PageResult page = PageResult.builder().pageNo(1)
                .pngBytes(new byte[]{1, 2, 3}).layoutPassed(true).build();

        processor(pipeline(true, page)).process(tempDir, "exec-png", barOutput());

        Path png = tempDir.resolve("dataviz/exec-png/report.png");
        assertTrue(Files.exists(png));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(png));
    }

    @Test
    @DisplayName("渲染引擎不健康 → 只留 HTML，PNG 静默跳过")
    void process_unhealthyPipeline_skipsPngWithoutHtmlLoss() throws Exception {
        processor(pipeline(false)).process(tempDir, "exec-unhealthy", barOutput());

        assertTrue(Files.exists(tempDir.resolve("dataviz/exec-unhealthy/report.html")));
        assertFalse(Files.exists(tempDir.resolve("dataviz/exec-unhealthy/report.png")));
    }

    @Test
    @DisplayName("非白名单图型 → 跳过该图不抛异常")
    void process_invalidSpec_skipsChartDoesNotThrow() {
        Map<String, Object> chart = Map.of(
                "chartType", "sankey", // 非白名单
                "title", "t", "evidence", List.of("i1"),
                "encoding", Map.of("x", "a", "y", "b"));
        Map<String, Object> output = Map.of(
                "rawData", "a,b\n1,2\n", "dataFormat", "csv",
                "chartSpecs", Map.of("charts", List.of(chart)));

        assertDoesNotThrow(() -> processor(null).process(tempDir, "exec-2", output));
    }

    @Test
    @DisplayName("缺 rawData/dataFormat → 静默返回")
    void process_missingRawData_doesNotThrow() {
        assertDoesNotThrow(() ->
                processor(null).process(tempDir, "exec-3", Map.of("chartSpecs", Map.of())));
    }

    @Test
    @DisplayName("输出为空 / 图表清单缺失 → 静默返回")
    void process_emptyOutputAndMissingCharts_doesNotThrow() {
        assertDoesNotThrow(() -> processor(null).process(tempDir, "exec-4", Map.of()));
        assertDoesNotThrow(() -> processor(null).process(tempDir, "exec-5",
                Map.of("rawData", "a,b\n1,2\n", "dataFormat", "csv")));
    }

    @Test
    @DisplayName("PNG 渲染抛异常 → HTML 已保留，异常不外抛")
    void process_pngRenderThrows_keepsHtmlAndSwallowsException() throws Exception {
        CardRenderPipeline broken = mock(CardRenderPipeline.class);
        when(broken.isHealthy()).thenReturn(true);
        when(broken.render(any(), eq("exec-boom"))).thenThrow(new RuntimeException("浏览器崩溃"));

        assertDoesNotThrow(() -> processor(broken).process(tempDir, "exec-boom", barOutput()));

        assertTrue(Files.exists(tempDir.resolve("dataviz/exec-boom/report.html")));
        assertFalse(Files.exists(tempDir.resolve("dataviz/exec-boom/report.png")));
    }

    @Test
    @DisplayName("rawData/dataFormat 取自 inputData，chartSpecs 取自阶段输出")
    void withInputs_mergesInputDataAndPhaseOutput() {
        SkillExecutionPo po = new SkillExecutionPo();
        po.setSkillExecutionId("exec-merge");
        po.setInputData("{\"rawData\":\"title,views\\nA,100\\n\",\"dataFormat\":\"csv\"}");

        Map<String, Object> merged = processor(null).withInputs(po,
                Map.of("chartSpecs", Map.of("charts", List.of())));

        assertEquals("csv", merged.get("dataFormat"));
        assertTrue(merged.containsKey("rawData"));
        assertTrue(merged.containsKey("chartSpecs"));
    }

    @Test
    @DisplayName("inputData 为脏 JSON → 仍保留阶段输出，不抛异常")
    void withInputs_brokenInputData_keepsPhaseOutput() {
        SkillExecutionPo po = new SkillExecutionPo();
        po.setSkillExecutionId("exec-dirty");
        po.setInputData("{not json");

        Map<String, Object> merged = processor(null).withInputs(po, Map.of("chartSpecs", Map.of()));

        assertTrue(merged.containsKey("chartSpecs"));
    }
}
