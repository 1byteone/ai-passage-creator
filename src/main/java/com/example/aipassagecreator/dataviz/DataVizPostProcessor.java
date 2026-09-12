package com.example.aipassagecreator.dataviz;

import com.example.aipassagecreator.card.CardRenderPipeline;
import com.example.aipassagecreator.card.model.PageResult;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.utils.GsonUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * dataviz Skill 收尾：解析原始数据 → 校验 Chart Spec → 渲染 HTML（+Playwright PNG）。
 * <p>
 * 静默降级：解析失败、图型非法、渲染引擎不可用都只记日志，绝不影响 Skill 主流程
 * （与 {@code ComicJournalService.processAsync} 同约定）。
 * <p>
 * <b>数据来源</b>：{@code rawData}/{@code dataFormat} 是 Skill 的 INPUT 变量，终态后只存在于
 * {@code po.getInputData()}；{@code getPersistedOutput()} 里只有各 phase 的 outputKey
 * （{@code chartSpecs}）。二者缺一不可，故此处按需合并。
 */
@Slf4j
@Component
public class DataVizPostProcessor {

    private static final String HTML_FILE = "report.html";
    private static final String PNG_FILE = "report.png";
    private static final String REPORT_TITLE = "数据图表报告";

    private final DatasetParser datasetParser;
    private final DatasetValidator datasetValidator;
    private final ChartSpecValidator chartSpecValidator;
    private final ChartHtmlRenderer chartHtmlRenderer;
    private final CardRenderPipeline cardRenderPipeline;
    private final DataVizStorageService dataVizStorageService;

    /** AI 多输出的未知字段不应让整份报告失败 */
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public DataVizPostProcessor(DatasetParser datasetParser,
                                DatasetValidator datasetValidator,
                                ChartSpecValidator chartSpecValidator,
                                ChartHtmlRenderer chartHtmlRenderer,
                                CardRenderPipeline cardRenderPipeline,
                                DataVizStorageService dataVizStorageService) {
        this.datasetParser = datasetParser;
        this.datasetValidator = datasetValidator;
        this.chartSpecValidator = chartSpecValidator;
        this.chartHtmlRenderer = chartHtmlRenderer;
        this.cardRenderPipeline = cardRenderPipeline;
        this.dataVizStorageService = dataVizStorageService;
    }

    @Async("skillExecutor")
    public void processAsync(SkillExecutionPo po, Map<String, Object> output) {
        try {
            if (po == null || po.getSkillExecutionId() == null) {
                return;
            }
            // 与 DataVizStorageService.artifactDir 同一落盘位置
            Path base = dataVizStorageService.baseDir();
            process(base, po.getSkillExecutionId(), withInputs(po, output));
        } catch (Exception e) {
            log.warn("dataviz 报告生成失败（不影响 Skill 主流程）: executionId={}",
                    po == null ? null : po.getSkillExecutionId(), e);
        }
    }

    /**
     * 供异步入口与测试共用：在持久化输入上叠加阶段输出。
     * <p>inputData 解析失败时仍保留 output，图表规格本身足以决定报告成败。
     */
    Map<String, Object> withInputs(SkillExecutionPo po, Map<String, Object> output) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (po.getInputData() != null && !po.getInputData().isBlank()) {
            try {
                merged.putAll(GsonUtils.fromJson(po.getInputData(),
                        new TypeToken<Map<String, Object>>() {}));
            } catch (Exception e) {
                log.warn("dataviz inputData 解析失败，仅用阶段输出: executionId={}",
                        po.getSkillExecutionId());
            }
        }
        if (output != null) {
            merged.putAll(output);
        }
        return merged;
    }

    /**
     * @param baseDataDir 数据根目录；产物写入 {baseDataDir}/dataviz/{executionId}/
     */
    public void process(Path baseDataDir, String executionId, Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return;
        }
        Object raw = output.get("rawData");
        Object fmt = output.get("dataFormat");
        if (raw == null || fmt == null) {
            log.warn("dataviz 输出缺 rawData/dataFormat，跳过: executionId={}", executionId);
            return;
        }

        Dataset ds;
        DataQualityReport profile;
        try {
            ds = datasetParser.parse(fmt.toString(), raw.toString());
            profile = datasetValidator.validate(ds);
        } catch (Exception e) {
            log.warn("dataviz 数据不可用，跳过报告: executionId={}, reason={}", executionId, e.getMessage());
            return;
        }

        List<ChartSpec> specs = extractSpecs(output.get("chartSpecs"));
        List<String> fragments = new ArrayList<>();
        for (ChartSpec spec : specs) {
            try {
                chartSpecValidator.validate(spec, ds, profile);
                fragments.add(chartHtmlRenderer.renderChart(spec, ds));
            } catch (Exception e) {
                // 单图非法跳过，不拖垮整份报告
                log.warn("dataviz 图表规格非法，跳过: executionId={}, reason={}",
                        executionId, e.getMessage());
            }
        }
        if (fragments.isEmpty()) {
            log.warn("dataviz 无合法图表，跳过报告落盘: executionId={}", executionId);
            return;
        }

        Path dir = baseDataDir.resolve("dataviz").resolve(executionId);
        String html = chartHtmlRenderer.renderPage(REPORT_TITLE, resolveStyle(output, specs),
                fragments, profile.warnings());
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(HTML_FILE), html);
        } catch (Exception e) {
            log.warn("dataviz HTML 落盘失败: executionId={}", executionId, e);
            return;
        }

        writePng(dir, executionId, html);
    }

    /** PNG 是可选产物：渲染引擎不可用或渲染失败都只留 HTML */
    private void writePng(Path dir, String executionId, String html) {
        if (cardRenderPipeline == null || !cardRenderPipeline.isHealthy()) {
            log.info("渲染引擎不可用，dataviz 跳过 PNG: executionId={}", executionId);
            return;
        }
        try {
            List<PageResult> results = cardRenderPipeline.render(List.of(html), executionId);
            if (results.isEmpty() || results.get(0).getPngBytes() == null) {
                log.warn("PNG 渲染无产出（HTML 已保留）: executionId={}", executionId);
                return;
            }
            Files.write(dir.resolve(PNG_FILE), results.get(0).getPngBytes());
        } catch (Exception e) {
            log.warn("dataviz PNG 导出失败（HTML 已保留）: executionId={}", executionId, e);
        }
    }

    /**
     * 报告风格：优先取 Skill 的 INPUT 变量 style（用户在下单时选的），
     * 其次取首张图表的 style，最后兜底 glance。
     */
    static String resolveStyle(Map<String, Object> output, List<ChartSpec> specs) {
        Object input = output == null ? null : output.get("style");
        if (input instanceof String s && !s.isBlank()) {
            return s;
        }
        return specs.stream()
                .map(ChartSpec::style)
                .filter(s -> s != null && !s.isBlank())
                .findFirst()
                .orElse("glance");
    }

    private List<ChartSpec> extractSpecs(Object chartSpecsObj) {
        if (!(chartSpecsObj instanceof Map<?, ?> wrapper)
                || !(wrapper.get("charts") instanceof List<?> list)) {
            return List.of();
        }
        List<ChartSpec> specs = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map)) continue;
            try {
                specs.add(mapper.convertValue(item, ChartSpec.class));
            } catch (Exception e) {
                // 结构对不上（缺 encoding 等）由 ChartSpecValidator 兜底，此处仅丢弃畸形项
                log.warn("dataviz 图表规格无法解析，跳过: reason={}", e.getMessage());
            }
        }
        return specs;
    }
}
