# 数据可视化报告 Skill Phase 1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 `data-visualization-report` Skill 最小闭环：JSON/CSV 输入 → 程序校验与统计 → AI 结论规划与图型推荐（白名单 Chart Spec）→ HITL 确认 → 程序生成 ECharts HTML 报告 → Playwright PNG 导出。

**Architecture:** 纯后端 Skill 子系统：数据解析/校验/统计由 Java 程序完成；AI 只产出受限 Chart Spec（白名单图型 + `dataRef` 引用服务端数据快照）；报告 HTML 由程序渲染（服务端直出 SVG，不依赖模型生成脚本）；PNG 导出复用现有 `CardRenderPipeline` 新增的 JS 启用渲染通道。Skill 编排复用现有 `SkillRegistry`/`SkillExecutionService`/SSE。

**Tech Stack:** Spring Boot 3.5 / Java 21 / Jackson（已有）/ Hutool（已有）/ Playwright Java（已有）/ ECharts（仅内嵌于生成 HTML，通过本地资源）/ JUnit 5 + Mockito

**Spec:** `docs/data-visualization-report-design.md` + `docs/data-visualization-report-phase0.md`

## Global Constraints

- 许可证红线：不复制 `lieflat-charts` 任何代码、模板、色板、字体；只参考产品原则。
- 图表白名单首期仅 `line`、`bar`、`table`；`pie`/`scatter`/`area` 后置。
- 风格白名单仅 `mono`、`glance`、`editorial`。
- 模型不得生成可执行脚本/原始 ECharts option；只输出 Chart Spec JSON。
- 图表数据必须来自 `dataRef` 指向的服务端快照，禁止模型输出行数据。
- 单页报告默认最多 6 张图；每图必须绑定一个结论（evidence 非空）。
- 上限：单文件 512KB / 5000 行 / 50 列 / 单元格 200 字符。
- 用户可见错误消息用中文；日志英文。
- 构造器注入（`@RequiredArgsConstructor`），不用字段注入（除既有 `@Resource` 惯例外新代码一律构造器注入）。
- 无 Interface+Impl 单实现分层；Service 直接是 `@Service` 类。
- 测试命名：`ClassNameTest`，方法 `methodName_scenario_expectedResult()`。
- 提交前：`mvn test` 全绿；Conventional Commits + `Co-Authored-By: Claude <noreply@anthropic.com>`。
- 测试环境无 Playwright/浏览器时渲染相关测试必须可跳过（`playwright.enabled=false` 时优雅降级，断言不失败）。

---

### Task 1: 数据集解析器（JSON/CSV → Dataset）

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/dataviz/Dataset.java`
- Create: `src/main/java/com/example/aipassagecreator/dataviz/DatasetParseException.java`
- Create: `src/main/java/com/example/aipassagecreator/dataviz/DatasetParser.java`
- Test: `src/test/java/com/example/aipassagecreator/dataviz/DatasetParserTest.java`

**Interfaces:**
- Consumes: 无（Jackson、Hutool 已在 pom）。
- Produces:
  - `record Dataset(List<Map<String, String>> rows, List<String> headers)`（值统一为字符串，类型推断交给 Validator）
  - `DatasetParser.parse(String dataFormat, String rawData) throws DatasetParseException`，`dataFormat` 仅接受 `"json"` / `"csv"`
- 约束：行数 ≤1000、列数 ≤50、单元格 ≤200 字符、输入 ≤512KB（超限抛 `DatasetParseException`，中文消息）。

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DatasetParserTest {

    private final DatasetParser parser = new DatasetParser();

    @Test
    void parse_csv_basic_headersAndRows() {
        Dataset ds = parser.parse("csv", "month,revenue\n2026-01,100\n2026-02,120\n");
        assertEquals(List.of("month", "revenue"), ds.headers());
        assertEquals(2, ds.rows().size());
        assertEquals("2026-01", ds.rows().get(0).get("month"));
        assertEquals("120", ds.rows().get(1).get("revenue"));
    }

    @Test
    void parse_json_arrayOfObjects() {
        String json = "[{\"title\":\"A\",\"views\":100},{\"title\":\"B\",\"views\":200}]";
        Dataset ds = parser.parse("json", json);
        assertEquals(List.of("title", "views"), ds.headers());
        assertEquals(2, ds.rows().size());
        assertEquals("200", ds.rows().get(1).get("views"));
    }

    @Test
    void parse_csv_quotedValueWithComma() {
        Dataset ds = parser.parse("csv", "title,tags\n\"A,B\",\"x\"\n");
        assertEquals("A,B", ds.rows().get(0).get("title"));
    }

    @Test
    void parse_csv_rowShorterThanHeader_padWithEmpty() {
        Dataset ds = parser.parse("csv", "a,b,c\n1,2\n");
        assertEquals("", ds.rows().get(0).get("c"));
    }

    @Test
    void parse_csv_tooManyRows_throws() {
        StringBuilder sb = new StringBuilder("a\n");
        for (int i = 0; i < 1001; i++) sb.append(i).append('\n');
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> parser.parse("csv", sb.toString()));
        assertTrue(ex.getMessage().contains("行数"));
    }

    @Test
    void parse_empty_throws() {
        assertThrows(DatasetParseException.class, () -> parser.parse("csv", "  "));
        assertThrows(DatasetParseException.class, () -> parser.parse("json", "not json"));
    }

    @Test
    void parse_unknownFormat_throws() {
        assertThrows(DatasetParseException.class, () -> parser.parse("xml", "<a/>"));
    }

    @Test
    void parse_json_notArrayOfObjects_throws() {
        assertThrows(DatasetParseException.class, () -> parser.parse("json", "{\"a\":1}"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=DatasetParserTest`
Expected: 编译失败 `DatasetParser` 不存在

- [ ] **Step 3: 最小实现**

```java
package com.example.aipassagecreator.dataviz;

/** 数据集解析失败（格式非法/超限）。消息面向用户，中文。 */
public class DatasetParseException extends RuntimeException {
    public DatasetParseException(String message) { super(message); }
}
```

```java
package com.example.aipassagecreator.dataviz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * JSON/CSV → Dataset。值统一转字符串，类型推断归 DatasetValidator。
 * 上限：1000 行 / 50 列 / 单元格 200 字符 / 输入 512KB。
 */
@Component
public class DatasetParser {

    private static final int MAX_ROWS = 1000;
    private static final int MAX_COLS = 50;
    private static final int MAX_CELL = 200;
    private static final int MAX_INPUT = 512 * 1024;
    private static final String[] SUPPORTED = {"json", "csv"};

    private final ObjectMapper mapper = new ObjectMapper();

    public Dataset parse(String dataFormat, String rawData) {
        if (dataFormat == null || rawData == null || rawData.isBlank()) {
            throw new DatasetParseException("数据内容不能为空");
        }
        if (rawData.length() > MAX_INPUT) {
            throw new DatasetParseException("数据超过 512KB 上限，请精简后再试");
        }
        String fmt = dataFormat.toLowerCase();
        if (!Arrays.asList(SUPPORTED).contains(fmt)) {
            throw new DatasetParseException("不支持的数据格式: " + dataFormat);
        }
        List<Map<String, String>> rows = "json".equals(fmt) ? parseJson(rawData) : parseCsv(rawData);
        if (rows.isEmpty()) {
            throw new DatasetParseException("未解析到有效数据行");
        }
        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        if (headers.size() > MAX_COLS) {
            throw new DatasetParseException("列数超过 50 上限，请精简后再试");
        }
        return new Dataset(rows, headers);
    }

    private List<Map<String, String>> parseJson(String raw) {
        List<Map<String, Object>> rawRows;
        try {
            rawRows = mapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            throw new DatasetParseException("JSON 格式不正确，需为对象数组");
        }
        if (rawRows.size() > MAX_ROWS) {
            throw new DatasetParseException("行数超过 1000 上限，请精简后再试");
        }
        // LinkedHashSet 保序去重：重复列名保留首次出现
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, Object> rawRow : rawRows) {
            Map<String, String> row = new LinkedHashMap<>();
            rawRow.forEach((k, v) -> {
                String key = k == null ? "" : k.trim();
                if (key.isEmpty() || !seen.add(key) || row.size() >= MAX_COLS) return;
                String value = valueToString(rawRow.get(k));
                if (value.length() > MAX_CELL) {
                    throw new DatasetParseException("单元格超过 200 字符上限: 列 " + key);
                }
                row.put(key, value);
            });
            if (!row.isEmpty()) rows.add(row);
        }
        return rows;
    }

    private List<Map<String, String>> parseCsv(String raw) {
        // hutool CsvReader 已带引号处理，这里直接使用
        var reader = cn.hutool.core.text.csv.CsvUtil.getReader();
        List<cn.hutool.core.text.csv.CsvRow> csvRows = reader.readFromStr(raw).getRows();
        if (csvRows.isEmpty()) {
            throw new DatasetParseException("未解析到有效数据行");
        }
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < csvRows.get(0).size() && headers.size() < MAX_COLS; i++) {
            String h = csvRows.get(0).getRawList().get(i).trim();
            headers.add(h.isEmpty() ? "col" + (i + 1) : h);
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int r = 1; r < csvRows.size(); r++) {
            if (rows.size() >= MAX_ROWS) {
                throw new DatasetParseException("行数超过 1000 上限，请精简后再试");
            }
            List<String> cells = csvRows.get(r).getRawList();
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String value = c < cells.size() ? cells.get(c) : "";
                if (value.length() > MAX_CELL) {
                    throw new DatasetParseException("单元格超过 200 字符上限: 列 " + headers.get(c));
                }
                row.put(headers.get(c), value);
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new DatasetParseException("未解析到有效数据行");
        }
        return rows;
    }

    private String valueToString(Object v) {
        if (v == null) return "";
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
        return v.toString();
    }
}
```

```java
package com.example.aipassagecreator.dataviz;

import java.util.List;
import java.util.Map;

/** 解析后的数据集：值统一为字符串，类型推断由 DatasetValidator 负责 */
public record Dataset(List<Map<String, String>> rows, List<String> headers) {
    public Dataset {
        rows = List.copyOf(rows);
        headers = List.copyOf(headers);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -Dtest=DatasetParserTest`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/dataviz/ src/test/java/com/example/aipassagecreator/dataviz/DatasetParserTest.java
git commit -m "feat(dataviz): 数据集解析器 JSON/CSV → Dataset"
```

---

### Task 2: 数据校验与画像（类型推断 + 质量检查）

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/dataviz/FieldProfile.java`
- Create: `src/main/java/com/example/aipassagecreator/dataviz/DataQualityReport.java`
- Create: `src/main/java/com/example/aipassagecreator/dataviz/DatasetValidator.java`
- Test: `src/test/java/com/example/aipassagecreator/dataviz/DatasetValidatorTest.java`

**Interfaces:**
- Consumes: `DatasetParser.parse(...)` → `Dataset`
- Produces:
  - `record FieldProfile(String name, String semantic, boolean numeric)`，`semantic ∈ {"time","category","measure"}`
  - `record DataQualityReport(int rowCount, List<FieldProfile> fields, int missingCells, int duplicateRows, List<String> warnings)`（`warnings` 为中文消息）
  - `DatasetValidator.validate(Dataset dataset)`：阻断错误（0 有效行、重复表头）抛 `DatasetParseException`；质量问题进 `warnings`
- 日期格式识别：`yyyy-MM-dd`、`yyyy-MM`、`yyyy/MM/dd`；比例字段（0–1 浮点且列名含 rate/ratio/占比）semantic 仍为 `measure`

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DatasetValidatorTest {

    private final DatasetValidator validator = new DatasetValidator();

    private Dataset csv(String raw) {
        return new DatasetParser().parse("csv", raw);
    }

    @Test
    void validate_trendData_timeAndMeasureDetected() {
        Dataset ds = csv("month,revenue\n2026-01,100\n2026-02,120\n2026-03,150\n");
        DataQualityReport r = validator.validate(ds);
        assertEquals("time", r.fields().get(0).semantic());
        assertEquals("measure", r.fields().get(1).semantic());
        assertTrue(r.fields().get(1).numeric());
        assertEquals(0, r.duplicateRows());
    }

    @Test
    void validate_categoryFieldDetected() {
        Dataset ds = csv("title,views\nA,100\nB,200\n");
        DataQualityReport r = validator.validate(ds);
        assertEquals("category", r.fields().get(0).semantic());
        assertEquals("measure", r.fields().get(1).semantic());
    }

    @Test
    void validate_missingCellsCountedAndWarned() {
        Dataset ds = csv("month,revenue\n2026-01,100\n,120\n");
        DataQualityReport r = validator.validate(ds);
        assertEquals(1, r.missingCells());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("缺失")));
    }

    @Test
    void validate_duplicateRowsCountedAndWarned() {
        Dataset ds = csv("a,b\n1,2\n1,2\n1,3\n");
        DataQualityReport r = validator.validate(ds);
        assertEquals(1, r.duplicateRows());
    }

    @Test
    void validate_noValidMeasureColumns_blocked() {
        Dataset ds = csv("name,city\n张三,北京\n李四,上海\n");
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator.validate(ds));
        assertTrue(ex.getMessage().contains("数值"));
    }

    @Test
    void validate_duplicateHeader_blocked() {
        // JSON 侧同名键去重，CSV 侧同名表头阻断
        assertThrows(DatasetParseException.class,
                () -> validator.validate(csv("a,a\n1,2\n")));
    }

    @Test
    void validate_nonNumericMeasureWarning() {
        Dataset ds = csv("month,revenue\n2026-01,abc\n2026-02,120\n");
        DataQualityReport r = validator.validate(ds);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("revenue")));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=DatasetValidatorTest`
Expected: 编译失败

- [ ] **Step 3: 最小实现**

```java
package com.example.aipassagecreator.dataviz;

/** 字段画像：语义角色 + 是否数值 */
public record FieldProfile(String name, String semantic, boolean numeric) {
    public static final String TIME = "time";
    public static final String CATEGORY = "category";
    public static final String MEASURE = "measure";
}
```

```java
package com.example.aipassagecreator.dataviz;

import java.util.List;

/** 数据质量画像：含中文警告，随报告展示 */
public record DataQualityReport(
        int rowCount,
        List<FieldProfile> fields,
        int missingCells,
        int duplicateRows,
        List<String> warnings) {
}
```

```java
package com.example.aipassagecreator.dataviz;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 数据校验与画像：类型推断（time/category/measure）、缺失/重复统计。
 * 阻断错误抛 DatasetParseException；质量问题写入 warnings 继续流程。
 */
@Component
public class DatasetValidator {

    private static final String[] DATE_PATTERNS = {
            "\\d{4}-\\d{2}-\\d{2}", "\\d{4}-\\d{2}", "\\d{4}/\\d{2}/\\d{2}"};

    public DataQualityReport validate(Dataset ds) {
        // 表头去重：JSON 去重过，CSV 可能出现重复表头
        Set<String> seen = new HashSet<>();
        for (String h : ds.headers()) {
            if (!seen.add(h)) {
                throw new DatasetParseException("存在重复列名: " + h);
            }
        }

        List<String> warnings = new ArrayList<>();
        int missingCells = 0;
        int duplicateRows = 0;
        Set<String> rowKeys = new HashSet<>();

        for (Map<String, String> row : ds.rows()) {
            String key = String.join("", row.values());
            if (!rowKeys.add(key)) duplicateRows++;
            for (String h : ds.headers()) {
                if (row.get(h) == null || row.get(h).isBlank()) missingCells++;
            }
        }
        if (missingCells > 0) {
            warnings.add("检测到 " + missingCells + " 个缺失值，相关单元格按空处理");
        }
        if (duplicateRows > 0) {
            warnings.add("检测到 " + duplicateRows + " 行重复数据，图表计算时保留原值");
        }

        List<FieldProfile> fields = ds.headers().stream()
                .map(h -> profileOf(ds, h)).toList();

        // 至少一列数值，否则后续无图可画
        if (fields.stream().noneMatch(f -> f.numeric())) {
            throw new DatasetParseException("未找到数值列，无法生成图表");
        }

        // 首列若为日期格式则标记为时间轴
        if (fields.size() > 1 && fields.get(0).semantic().equals(FieldProfile.CATEGORY)
                && isDateColumn(ds, ds.headers().get(0))) {
            fields.set(0, new FieldProfile(ds.headers().get(0), FieldProfile.TIME, false));
        }

        if (missingCells > 0 || duplicateRows > 0 || fields.stream().anyMatch(f ->
                f.semantic().equals(FieldProfile.MEASURE)
                        && !isFullyNumeric(ds, f.name()) && !isBlankAllowed(ds, f.name()))) {
            // 非法数值已计入警告
        }
        fields.stream().filter(f -> f.numeric() && hasNonNumeric(ds, f.name()))
                .forEach(f -> warnings.add("数值列 " + f.name() + " 存在非法值，计算时按 0 处理"));

        return new DataQualityReport(ds.rows().size(), fields, missingCells, duplicateRows,
                List.copyOf(warnings));
    }

    private FieldProfile profileOf(Dataset ds, String col) {
        boolean numeric = isNumericColumn(ds, col);
        if (!numeric) {
            // 全日期格式 → time；否则 category
            return isDateColumn(ds, col)
                    ? new FieldProfile(col, FieldProfile.TIME, false)
                    : new FieldProfile(col, FieldProfile.CATEGORY, false);
        }
        return new FieldProfile(col, FieldProfile.MEASURE, true);
    }

    private boolean isNumericColumn(Dataset ds, String col) {
        boolean any = false;
        for (Map<String, String> row : ds.rows()) {
            String v = row.get(col);
            if (v == null || v.isBlank()) continue;
            any = true;
            try {
                Double.parseDouble(v.trim().replace(",", ""));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return any;
    }

    private boolean isDateColumn(Dataset ds, String col) {
        boolean any = false;
        for (Map<String, String> row : ds.rows()) {
            String v = row.get(col);
            if (v == null || v.isBlank()) continue;
            any = true;
            String t = v.trim();
            boolean match = false;
            for (String p : DATE_PATTERNS) {
                if (t.matches(p)) { match = true; break; }
            }
            if (!match) return false;
        }
        return any;
    }

    private boolean hasNonNumeric(Dataset ds, String col) {
        for (Map<String, String> row : ds.rows()) {
            String v = row.get(col);
            if (v == null || v.isBlank()) continue;
            try {
                Double.parseDouble(v.trim().replace(",", ""));
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlankAllowed(Dataset ds, String col) {
        return true; // 缺失已在 missingCells 统一提示
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -Dtest=DatasetValidatorTest`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/dataviz/ src/test/java/com/example/aipassagecreator/dataviz/DatasetValidatorTest.java
git commit -m "feat(dataviz): 数据校验与字段画像"
```

---

### Task 3: Chart Spec 模型与白名单校验器

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/dataviz/ChartSpec.java`
- Create: `src/main/java/com/example/aipassagecreator/dataviz/ChartSpecValidator.java`
- Test: `src/test/java/com/example/aipassagecreator/dataviz/ChartSpecValidatorTest.java`

**Interfaces:**
- Consumes: `Dataset`、`DataQualityReport`
- Produces:
  - `record ChartSpec(String chartType, String style, String title, String subtitle, String source, String unit, String insightId, Encoding encoding, Sort sort, List<String> evidence, Options options)`
  - `record Encoding(String x, String y, String color)`
  - `record Sort(String field, String direction)`
  - `record Insight(String id, String claim, List<String> evidenceFields, String importance)`（Phase 4 AI 输出的结论）
  - `ChartSpecValidator.validate(ChartSpec spec, Dataset ds, DataQualityReport profile)`：非法即抛 `DatasetParseException`（中文消息）
- 白名单：`chartType ∈ {line, bar, table}`；`style ∈ {mono, glance, editorial}`；title ≤80 字符且非空；`line` 的 x 必须是 time 字段、y 必须是 measure；`bar` 的 x 是 category、y 是 measure；`table` 允许 encoding 全空（展示全部列）

- [ ] **Step 1: 写失败测试**

```java
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
                "i1", new ChartSpec.Encoding("title", "views", null), null,
                List.of("i1"), List.of());
        assertDoesNotThrow(() -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_unknownChartType_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("sankey", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("month", "revenue", null), null,
                List.of("i1"), List.of());
        DatasetParseException ex = assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
        assertTrue(ex.getMessage().contains("图表类型"));
    }

    @Test
    void validate_lineWithoutTimeX_blocked() {
        var c = categoryCtx();
        ChartSpec spec = new ChartSpec("line", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("title", "views", null), null,
                List.of("i1"), List.of());
        assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_yFieldNotMeasure_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("bar", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("month", "month", null), null,
                List.of("i1"), List.of());
        assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_unknownField_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("line", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("nope", "revenue", null), null,
                List.of("i1"), List.of());
        assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_emptyEvidence_blocked() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("line", "mono", "t", null, "s", null,
                "i1", new ChartSpec.Encoding("month", "revenue", null), null,
                List.of(), List.of());
        assertThrows(DatasetParseException.class,
                () -> validator2.validate(spec, c.ds(), c.profile()));
    }

    @Test
    void validate_tableWithoutEncoding_passes() {
        var c = trendCtx();
        ChartSpec spec = new ChartSpec("table", "mono", "明细", null, "s", null,
                "i1", new ChartSpec.Encoding(null, null, null), null,
                List.of("i1"), List.of());
        assertDoesNotThrow(() -> validator2.validate(spec, c.ds(), c.profile()));
    }

    private Ctx trendCtx() { return new Ctx(
            parser.parse("csv", "month,revenue\n2026-01,100\n2026-02,120\n"),
            null); }
}
```

注：`Ctx` 辅助 record 内部直接 `validator.validate(ds)`，实际写测试时统一为：

```java
    private Ctx trendCtx() {
        Dataset ds = parser.parse("csv", "month,revenue\n2026-01,100\n2026-02,120\n");
        return new Ctx(ds, validator.validate(ds));
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=ChartSpecValidatorTest`
Expected: 编译失败

- [ ] **Step 3: 最小实现**

```java
package com.example.aipassagecreator.dataviz;

import java.util.List;

/** 受限图表规格：AI 只能产出此结构，渲染程序只认这些字段 */
public record ChartSpec(
        String chartType,
        String style,
        String title,
        String subtitle,
        String source,
        String unit,
        String insightId,
        Encoding encoding,
        Sort sort,
        List<String> evidence,
        List<String> annotations) {

    public record Encoding(String x, String y, String color) {}
    public record Sort(String field, String direction) {}
}
```

```java
package com.example.aipassagecreator.dataviz;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Chart Spec 白名单校验：非法即拒绝，消息面向用户 */
@Component
public class ChartSpecValidator {

    private static final Set<String> CHART_TYPES = Set.of("line", "bar", "table");
    private static final Set<String> STYLES = Set.of("mono", "glance", "editorial");

    public void validate(ChartSpec spec, Dataset ds, DataQualityReport profile) {
        if (spec == null) throw new DatasetParseException("图表规格缺失");
        if (!CHART_TYPES.contains(spec.chartType())) {
            throw new DatasetParseException("不支持的图表类型: " + spec.chartType());
        }
        if (spec.style() == null || !STYLES.contains(spec.style())) {
            throw new DatasetParseException("不支持的视觉风格: " + spec.style());
        }
        if (spec.title() == null || spec.title().isBlank() || spec.title().length() > 80) {
            throw new DatasetParseException("图表标题为空或超过 80 字");
        }
        if (spec.evidence() == null || spec.evidence().isEmpty()) {
            throw new DatasetParseException("图表缺少关联结论");
        }
        if ("table".equals(spec.chartType())) {
            return; // table 允许全空 encoding，展示全部列
        }
        ChartSpec.Encoding enc = spec.encoding();
        if (enc == null) {
            throw new DatasetParseException("图表缺少数据映射");
        }
        FieldProfile xField = fieldOf(profile, enc.x());
        FieldProfile yField = fieldOf(profile, enc.y());
        requireField(ds, profile, enc.x());
        requireField(ds, profile, enc.y());
        if ("line".equals(spec.chartType())) {
            if (!FieldProfile.TIME.equals(xField.semantic())) {
                throw new DatasetParseException("折线图的横轴必须为时间字段");
            }
        }
        if ("bar".equals(spec.chartType())) {
            if (!FieldProfile.CATEGORY.equals(xField.semantic())) {
                throw new DatasetParseException("柱状图的横轴必须为分类字段");
            }
        }
        if (!FieldProfile.MEASURE.equals(yField.semantic())) {
            throw new DatasetParseException("纵轴必须为数值字段");
        }
    }

    private FieldProfile fieldOf(DataQualityReport profile, String name) {
        return profile.fields().stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new DatasetParseException("字段不存在: " + name));
    }

    private void requireField(Dataset ds, DataQualityReport profile, String name) {
        if (name == null || name.isBlank()) {
            throw new DatasetParseException("数据映射字段为空");
        }
        fieldOf(profile, name);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -Dtest=ChartSpecValidatorTest`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/dataviz/ src/test/java/com/example/aipassagecreator/dataviz/ChartSpecValidatorTest.java
git commit -m "feat(dataviz): ChartSpec 模型与白名单校验器"
```

---

### Task 4: 统计分析器（趋势/排名程序计算）

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/dataviz/DatasetStats.java`
- Create: `src/main/java/com/example/aipassagecreator/dataviz/StatsService.java`
- Test: `src/test/java/com/example/aipassagecreator/dataviz/StatsServiceTest.java`

**Interfaces:**
- Consumes: `Dataset`、`DataQualityReport`
- Produces:
  - `record DatasetStats(double min, double max, double sum, double avg, double first, double last, double changeRate, int validCount)`（按单数值列计算）
  - `StatsService.computeFieldStats(Dataset ds, DataQualityReport profile, String field) → DatasetStats`
  - `StatsService.rankBy(Dataset ds, DataQualityReport profile, String categoryField, String measureField, int topN) → List<Map<String,String>>`（降序，程序排序）
  - 非法数值按 0 参与计算（与 Validator 警告口径一致）
- 所有数值计算由本服务完成，AI 不得复算

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StatsServiceTest {

    private final DatasetParser parser = new DatasetParser();
    private final DatasetValidator validator = new DatasetValidator();
    private final StatsService stats = new StatsService();

    @Test
    void computeFieldStats_growthRateCalculated() {
        Dataset ds = parser.parse("csv",
                "month,revenue\n2026-01,100\n2026-02,150\n2026-03,120\n");
        DataQualityReport p = validator.validate(ds);
        DatasetStats s = stats.computeFieldStats(ds, p, "revenue");
        assertEquals(100.0, s.min());
        assertEquals(150.0, s.max());
        assertEquals(370.0, s.sum());
        assertEquals(120.0, s.first());
        assertEquals(120.0, s.last());
        assertEquals(0.2, s.changeRate(), 1e-9);
        assertEquals(3, s.validCount());
    }

    @Test
    void computeFieldStats_illegalValueCountsAsZero() {
        Dataset ds = parser.parse("csv", "month,revenue\n2026-01,abc\n2026-02,100\n");
        DataQualityReport p = validator.validate(ds);
        DatasetStats s = stats.computeFieldStats(ds, p, "revenue");
        assertEquals(100.0, s.sum());
        assertEquals(0.0, s.first());
    }

    @Test
    void rankBy_descendingOrder() {
        Dataset ds = parser.parse("json",
                "[{\"t\":\"A\",\"v\":50},{\"t\":\"C\",\"v\":200},{\"t\":\"B\",\"v\":100}]");
        DataQualityReport p = validator.validate(ds);
        List<Map<String, String>> ranked = stats.rankBy(ds, p, "t", "v", 10);
        assertEquals("C", ranked.get(0).get("t"));
        assertEquals("B", ranked.get(1).get("t"));
        assertEquals("A", ranked.get(2).get("t"));
    }

    @Test
    void rankBy_topNLimits() {
        Dataset ds = parser.parse("json",
                "[{\"t\":\"A\",\"v\":1},{\"t\":\"B\",\"v\":2},{\"t\":\"C\",\"v\":3}]");
        DataQualityReport p = validator.validate(ds);
        assertEquals(2, stats.rankBy(ds, p, "t", "v", 2).size());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=StatsServiceTest`
Expected: 编译失败

- [ ] **Step 3: 最小实现**

```java
package com.example.aipassagecreator.dataviz;

/** 单数值列统计量；changeRate = (last-first)/|first|，first=0 时为 0 */
public record DatasetStats(
        double min, double max, double sum, double avg,
        double first, double last, double changeRate, int validCount) {
}
```

```java
package com.example.aipassagecreator.dataviz;

import org.springframework.stereotype.Component;

import java.util.*;

/** 程序侧统计计算：AI 只解释结果，不做数值运算 */
@Component
public class StatsService {

    public DatasetStats computeFieldStats(Dataset ds, DataQualityReport profile, String field) {
        List<Double> values = numericValues(ds, field);
        if (values.isEmpty()) {
            throw new DatasetParseException("数值列无有效数据: " + field);
        }
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double first = values.get(0);
        double last = values.get(values.size() - 1);
        double changeRate = first == 0 ? 0 : (last - first) / Math.abs(first);
        return new DatasetStats(
                values.stream().mapToDouble(Double::doubleValue).min().orElse(0),
                values.stream().mapToDouble(Double::doubleValue).max().orElse(0),
                sum,
                sum / values.size(),
                first, last, changeRate, values.size());
    }

    public List<Map<String, String>> rankBy(Dataset ds, DataQualityReport profile,
                                            String categoryField, String measureField, int topN) {
        List<Map<String, String>> rows = new ArrayList<>(ds.rows());
        rows.sort((a, b) -> Double.compare(
                toDouble(rowsValue(b, categoryField, measureFieldOf(profile))),
                toDouble(rowsValue(a, categoryField, measureFieldOf(profile)))));
        // 上面的 comparator 写法依赖 measure 字段名，简化为直接按 measure 排序：
        return rows.stream().limit(topN).toList();
    }

    private String measureFieldOf(DataQualityReport profile) {
        return profile.fields().stream()
                .filter(f -> FieldProfile.MEASURE.equals(f.semantic()))
                .map(FieldProfile::name)
                .findFirst()
                .orElseThrow(() -> new DatasetParseException("未找到数值列"));
    }

    private String rowsValue(Map<String, String> row, String category, String measure) {
        return row.get(measure);
    }

    private double toDouble(String v) {
        try {
            return Double.parseDouble(v == null || v.isBlank() ? "0" : v.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<Double> numericValues(Dataset ds, String field) {
        List<Double> out = new ArrayList<>();
        for (Map<String, String> row : ds.rows()) {
            out.add(toDouble(row.get(field)));
        }
        return out;
    }
}
```

注意：`rankBy` 的 comparator 实现按“每行取 measure 值降序”重写：

```java
    public List<Map<String, String>> rankBy(Dataset ds, DataQualityReport profile,
                                            String categoryField, String measureField, int topN) {
        List<Map<String, String>> rows = new ArrayList<>(ds.rows());
        rows.sort((a, b) -> Double.compare(toDouble(b.get(measureField)), toDouble(a.get(measureField))));
        return rows.stream().limit(topN).toList();
    }
```

（计划给出两个版本是为评审留痕；实现时只保留上面这个签名版本，删除辅助方法 `rowsValue`/`measureFieldOf` 中未被使用者。）

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -Dtest=StatsServiceTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/dataviz/ src/test/java/com/example/aipassagecreator/dataviz/StatsServiceTest.java
git commit -m "feat(dataviz): 统计分析器（趋势/排名程序计算）"
```

---

### Task 5: Chart Spec 渲染器（Spec → 服务端 SVG HTML）

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/dataviz/ChartHtmlRenderer.java`
- Test: `src/test/java/com/example/aipassagecreator/dataviz/ChartHtmlRendererTest.java`

**Interfaces:**
- Consumes: `ChartSpec`（已通过校验）、`Dataset`、`DataQualityReport`、`StatsService`
- Produces:
  - `String renderChart(ChartSpec spec, Dataset ds, DataQualityReport profile) → 独立 HTML 片段`（单卡：标题/副标题/单位/来源 + 图形）
  - `String renderPage(String chartTitle, List<String> chartFragments, List<String> warnings) → 完整 HTML 文档`（单文件、无外部 CDN、无 JS——服务端直出 SVG，保证 Playwright JS 禁用环境下可截图）
- 渲染规则：
  - `bar`：横向条形，宽度 = 值/max 比例，纯 SVG rect + text
  - `line`：折线 polyline，x 均分，y 按 min/max 归一
  - `table`：纯 HTML table
  - 风格差异只体现在 CSS 颜色与字号（mono 灰阶、glance 强调色块、editorial 细线留白），不允许任何 `<script>`
  - 所有动态文本必须经 `escapeHtml`（含 `<` `>` `&` `"` 转义），防注入

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChartHtmlRendererTest {

    private final DatasetParser parser = new DatasetParser();
    private final DatasetValidator validator = new DatasetValidator();
    private final ChartHtmlRenderer renderer = new ChartHtmlRenderer();

    private Dataset trend() {
        return parser.parse("csv", "month,revenue\n2026-01,100\n2026-02,120\n2026-03,150\n");
    }

    @Test
    void renderChart_bar_containsSvgRectsAndEscapedTitle() {
        Dataset ds = trend();
        ChartSpec spec = new ChartSpec("bar", "glance", "收入对比<script>", null, "测试", "元",
                "i1", new ChartSpec.Encoding("month", "revenue", null), null,
                List.of("i1"), List.of());
        String html = renderer.renderChart(spec, ds, validator.validate(ds));
        assertTrue(html.contains("rect"));
        assertTrue(html.contains("收入对比&lt;script&gt;"));
        assertFalse(html.contains("<script"));
    }

    @Test
    void renderChart_line_containsPolyline() {
        Dataset ds = trend();
        ChartSpec spec = new ChartSpec("line", "mono", "收入趋势", null, "测试", "元",
                "i1", new ChartSpec.Encoding("month", "revenue", null), null,
                List.of("i1"), List.of());
        String html = renderer.renderChart(spec, ds, validator.validate(ds));
        assertTrue(html.contains("polyline"));
    }

    @Test
    void renderChart_table_containsHtmlTable() {
        Dataset ds = trend();
        ChartSpec spec = new ChartSpec("table", "mono", "明细", null, "测试", null,
                "i1", new ChartSpec.Encoding(null, null, null), null,
                List.of("i1"), List.of());
        String html = renderer.renderChart(spec, ds, validator.validate(ds));
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
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=ChartHtmlRendererTest`
Expected: 编译失败

- [ ] **Step 3: 最小实现**

```java
package com.example.aipassagecreator.dataviz;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ChartSpec → 服务端 SVG/HTML 渲染。无 JS、无 CDN，Playwright 禁 JS 也能截图。
 * 动态文本一律 escapeHtml，防 HTML 注入。
 */
@Component
public class ChartHtmlRenderer {

    private static final int CHART_W = 640;
    private static final int CHART_H = 320;
    private static final int PAD = 48;

    public String renderChart(ChartSpec spec, Dataset ds, DataQualityReport profile) {
        String body = switch (spec.chartType()) {
            case "bar" -> renderBar(spec, ds);
            case "line" -> renderLine(spec, ds);
            case "table" -> renderTable(spec, ds);
            default -> throw new DatasetParseException("不支持的图表类型: " + spec.chartType());
        };
        StringBuilder sb = new StringBuilder();
        sb.append("<section class=\"chart-card ").append(styleClass(spec.style())).append("\">");
        sb.append("<h3>").append(escapeHtml(spec.title())).append("</h3>");
        if (spec.subtitle() != null && !spec.subtitle().isBlank()) {
            sb.append("<p class=\"subtitle\">").append(escapeHtml(spec.subtitle())).append("</p>");
        }
        sb.append(body);
        sb.append("<p class=\"meta\">");
        if (spec.unit() != null) sb.append("单位: ").append(escapeHtml(spec.unit())).append(" · ");
        sb.append("来源: ").append(escapeHtml(spec.source() == null ? "用户提供" : spec.source()));
        sb.append("</p></section>");
        return sb.toString();
    }

    public String renderPage(String title, List<String> chartFragments, List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">")
          .append("<title>").append(escapeHtml(title)).append("</title><style>")
          .append("body{font-family:sans-serif;color:#222;max-width:760px;margin:24px auto;padding:0 16px}")
          .append("h1{font-size:24px}.chart-card{border:1px solid #ddd;border-radius:8px;padding:16px;margin:16px 0}")
          .append(".subtitle{color:#666}.meta{color:#888;font-size:12px}")
          .append("table{border-collapse:collapse;width:100%}td,th{border:1px solid #ccc;padding:4px 8px;font-size:13px}")
          .append(".glance .bar-fill{fill:#2563eb}.mono .bar-fill{fill:#555}.editorial .bar-fill{fill:#374151}")
          .append(".warning{background:#fff7ed;border:1px solid #fdba74;padding:8px 12px;border-radius:6px;font-size:13px}")
          .append("</style></head><body>");
        sb.append("<h1>").append(escapeHtml(title)).append("</h1>");
        if (warnings != null && !warnings.isEmpty()) {
            for (String w : warnings) {
                sb.append("<div class=\"warning\">").append(escapeHtml(w)).append("</div>");
            }
        }
        for (String f : chartFragments) sb.append(f);
        sb.append("</body></html>");
        return sb.toString();
    }

    private String renderBar(ChartSpec spec, Dataset ds) {
        String x = spec.encoding().x(), y = spec.encoding().y();
        List<Map<String, String>> rows = new java.util.ArrayList<>(ds.rows());
        rows.sort((a, b) -> Double.compare(toD(b.get(y)), toD(a.get(y))));
        double max = rows.stream().mapToDouble(r -> toD(r.get(y))).max().orElse(1);
        if (max == 0) max = 1;
        int barH = 32;
        int innerH = rows.size() * (barH + 8);
        int innerW = CHART_W - PAD * 2;
        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox=\"0 0 ").append(CHART_W).append(' ').append(Math.max(innerH + PAD, CHART_H))
          .append("\" width=\"100%\">");
        for (int i = 0; i < rows.size(); i++) {
            double v = toD(rows.get(i).get(y));
            int w = (int) Math.round(v / max * innerW);
            int yPos = PAD + i * (barH + 8);
            sb.append("<text x=\"").append(PAD).append("\" y=\"").append(yPos + barH / 2 + 4)
              .append("\" font-size=\"12\">").append(escapeHtml(rows.get(i).get(x))).append("</text>");
            sb.append("<rect class=\"bar-fill\" x=\"").append(PAD + 90).append("\" y=\"").append(yPos)
              .append("\" width=\"").append(Math.max(1, (int) (v / max * (innerW - 90))))
              .append("\" height=\"").append(barH).append("\" rx=\"3\"/>");
            sb.append("<text x=\"").append(CHART_W - PAD).append("\" y=\"").append(yPos + barH / 2 + 4)
              .append("\" font-size=\"12\" text-anchor=\"end\">").append(fmt(v)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private String renderLine(ChartSpec spec, Dataset ds) {
        String x = spec.encoding().x(), y = spec.encoding().y();
        List<Double> vals = ds.rows().stream().map(r -> toD(r.get(y))).toList();
        double min = vals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = vals.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max == min) max = min + 1;
        int innerW = CHART_W - PAD * 2;
        int innerH = CHART_H - PAD * 2;
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < vals.size(); i++) {
            double px = PAD + (vals.size() == 1 ? innerW / 2.0 : innerW * i / (vals.size() - 1.0));
            double py = PAD + innerH - (vals.get(i) - min) / (max - min) * innerH;
            if (i > 0) pts.append(' ');
            pts.append(String.format("%.1f,%.1f", px, py));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox=\"0 0 ").append(CHART_W).append(' ').append(CHART_H)
          .append("\" width=\"100%\"><polyline fill=\"none\" stroke=\"#2563eb\" stroke-width=\"2\" points=\"")
          .append(pts).append("\"/>");
        // x 轴首尾标签
        if (!ds.rows().isEmpty()) {
            sb.append("<text x=\"").append(PAD).append("\" y=\"").append(CHART_H - 8)
              .append("\" font-size=\"11\">").append(escapeHtml(firstRowValue(ds, x))).append("</text>");
            sb.append("<text x=\"").append(CHART_W - PAD).append("\" y=\"").append(CHART_H - 8)
              .append("\" font-size=\"11\" text-anchor=\"end\">")
              .append(escapeHtml(lastRowValue(ds, x))).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private String renderTable(ChartSpec spec, Dataset ds) {
        StringBuilder sb = new StringBuilder("<table><thead><tr>");
        for (String h : ds.headers()) sb.append("<th>").append(escapeHtml(h)).append("</th>");
        sb.append("</tr></thead><tbody>");
        for (Map<String, String> row : ds.rows()) {
            sb.append("<tr>");
            for (String h : ds.headers()) sb.append("<td>").append(escapeHtml(row.get(h))).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String firstRowValue(Dataset ds, String col) {
        return ds.rows().isEmpty() ? "" : ds.rows().get(0).get(col);
    }

    private String lastRowValue(Dataset ds, String col) {
        return ds.rows().isEmpty() ? "" : ds.rows().get(ds.rows().size() - 1).get(col);
    }

    private String styleClass(String style) {
        return switch (style) {
            case "glance" -> "glance";
            case "editorial" -> "editorial";
            default -> "mono";
        };
    }

    private double toD(String v) {
        try {
            return Double.parseDouble(v == null || v.isBlank() ? "0" : v.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -Dtest=ChartHtmlRendererTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/dataviz/ src/test/java/com/example/aipassagecreator/dataviz/ChartHtmlRendererTest.java
git commit -m "feat(dataviz): ChartSpec 服务端 SVG 渲染器（无 JS 无 CDN）"
```

---

### Task 6: Skill YAML + Prompt（AI 只产出 Chart Spec JSON）

**Files:**
- Create: `src/main/resources/skills/data-visualization-report/skill.yaml`
- Create: `src/main/resources/skills/data-visualization-report/prompts/phase1_analyze.md`
- Create: `src/main/resources/skills/data-visualization-report/prompts/phase2_charts.md`
- Test: `src/test/java/com/example/aipassagecreator/dataviz/DataVizSkillRegistrationTest.java`

**Interfaces:**
- Consumes: 既有 Skill 引擎（`SkillRegistry` 自动扫描 `classpath*:skills/*/skill.yaml`）；Task 1–5 的 dataviz 组件不直接被 YAML 调用——Skill 负责让 AI 产出 Chart Spec，渲染复用在 Task 7 接线。
- Produces:
  - Skill 名称 `data-visualization-report`，变量 `rawData`/`dataFormat`/`goal`/`style`
  - phase1 `profile_dataset`（无确认），phase2 `recommend_charts`（`requireConfirmation: true`，JSON parser，`outputKey: chartSpecs`）
  - 前端/测试依赖: `skillRegistry.getSkill("data-visualization-report")` 返回 2 个 phase；phase2 需确认
- 输入约束：`rawData` 用 textarea uiType maxLength 200000；style 用 options `[mono, glance, editorial]` 默认 `glance`

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.dataviz;

import com.example.aipassagecreator.skill.PhaseDefinition;
import com.example.aipassagecreator.skill.SkillDefinition;
import com.example.aipassagecreator.skill.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DataVizSkillRegistrationTest {

    @Autowired(required = false)
    private com.example.aipassagecreator.skill.SkillRegistry skillRegistry;

    @Test
    void registration_skillExistsWithTwoPhases() {
        // 测试环境 dataviz skill 依赖 yaml 存在即可注册，不需要真实模型调用
        assertNotNull(skillRegistry, "SkillRegistry 未加载");
        com.example.aipassagecreator.skill.SkillDefinition def =
                skillRegistry.getSkill("data-visualization-report");
        assertEquals(2, def.getPhases().size());
    }

    @Test
    void registration_secondPhaseRequiresConfirmation() {
        com.example.aipassagecreator.skill.SkillDefinition def =
                skillRegistry.getSkill("data-visualization-report");
        PhaseDefinition p2 = def.getPhases().get(1);
        assertTrue(p2.isRequireConfirmation());
        assertEquals("json", p2.getOutputParser());
        assertEquals("chartSpecs", p2.getOutputKey());
    }

    @Test
    void registration_styleVariableHasWhitelist() {
        com.example.aipassagecreator.skill.SkillDefinition def =
                skillRegistry.getSkill("data-visualization-report");
        com.example.aipassagecreator.skill.VariableDef style = def.getVariables().get("style");
        assertNotNull(style.getOptions());
        assertEquals(3, style.getOptions().size());
    }
}
```

注意：`VariableDef` / `PhaseDefinition` 的实际包路径以现有 `SkillEngineIntegrationTest` 中的 import 为准（同包 `com.example.aipassagecreator.skill`）。若 `getOptions()` 返回元素类型不是 String，按 `SkillEngineIntegrationTest.testPublicSkillFieldMetadata` 中现有断言写法调整。

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=DataVizSkillRegistrationTest`
Expected: FAIL `Skill 不存在: data-visualization-report`

- [ ] **Step 3: 写 skill.yaml 与 prompts**

`src/main/resources/skills/data-visualization-report/skill.yaml`:

```yaml
name: data-visualization-report
description: 数据可视化与报告生成：上传 JSON/CSV，自动分析并生成图表报告
category: dataviz
requiredRoles: [user]

variables:
  rawData:
    description: 数据内容（JSON 对象数组或 CSV，含表头）
    required: true
    source: INPUT
    uiType: textarea
    maxLength: 200000
  dataFormat:
    description: 数据格式（json/csv）
    required: true
    source: INPUT
    defaultValue: csv
  goal:
    description: 分析目标（想说明什么问题、给谁看）
    required: false
    source: INPUT
  style:
    description: 视觉风格
    required: false
    source: INPUT
    defaultValue: glance
    options:
      - value: mono
        label: 黑白灰（保底）
      - value: glance
        label: 快速判断（周报/汇报）
      - value: editorial
        label: 细节阅读（研究报告）

phases:
  - name: profile_dataset
    promptFile: skills/data-visualization-report/prompts/phase1_profile.md
    model: agnes
    streaming: false
    outputParser: json
    outputKey: datasetProfile
    variables:
      - name: rawData
      - name: dataFormat
      - name: goal
    requireConfirmation: false

  - name: recommend_charts
    promptFile: skills/data-visualization-report/prompts/phase2_charts.md
    model: agnes
    streaming: false
    outputParser: json
    outputKey: chartSpecs
    variables:
      - name: datasetProfile
        ref: profile_dataset
      - name: goal
      - name: style
    requireConfirmation: true
```

`prompts/phase1_profile.md`:

```markdown
你是数据分析助手。用户将提供一份结构化数据（JSON 对象数组或 CSV）。

数据内容：
{{dataFormat}} 格式：
{{rawData}}

分析目标：{{goal}}

任务：识别数据契约。只输出 JSON，格式：
{
  "summary": "一句话描述数据集",
  "grain": "数据粒度（如 monthly/article/segment）",
  "fields": [
    {"name": "字段名", "semantic": "time|category|measure", "note": "业务含义"}
  ],
  "candidateInsights": [
    {"id": "i1", "claim": "一句话结论", "evidenceFields": ["字段名"], "importance": "high|medium|low"}
  ]
}

规则：
1. 最多 3 条 candidateInsights，每条必须有 evidenceFields 且字段名真实存在。
2. 不得编造数据或计算数值，只描述"哪个字段在什么范围呈现什么形态"。
3. 输出必须是合法 JSON，不要 markdown 代码块包裹。
```

`prompts/phase2_charts.md`:

```markdown
你是图表规格师。根据数据契约和结论，产出受限 Chart Spec JSON。

数据画像：
{{datasetProfile}}

分析目标：{{goal}}
视觉风格：{{style}}

输出格式（合法 JSON，不要 markdown 包裹）：
{
  "charts": [
    {
      "chartType": "line|bar|table",
      "style": "{{style}}",
      "title": "≤80字标题",
      "subtitle": "补充说明或null",
      "source": "用户提供数据",
      "unit": "单位或null",
      "insightId": "i1",
      "encoding": {"x": "字段名", "y": "字段名", "color": null},
      "sort": null,
      "evidence": ["i1"],
      "annotations": []
    }
  ]
}

硬性规则：
1. 只能使用 line、bar、table 三种图型。
2. encoding 中的字段名必须来自数据画像 fields。
3. line 的 x 必须是 time 字段；bar 的 x 必须是 category 字段；y 必须是 measure 字段。
4. 每张图绑定一个独立结论（insightId + evidence），最多 3 张图，不为凑数多画。
5. chartType/table 时 encoding 可为全 null。
6. 不得输出任何 JavaScript、HTML 或 ECharts 配置。
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -Dtest=DataVizSkillRegistrationTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/skills/data-visualization-report/ src/test/java/com/example/aipassagecreator/dataviz/DataVizSkillRegistrationTest.java
git commit -m "feat(dataviz): data-visualization-report Skill YAML 与提示词"
```

---

### Task 7: Skill→dataviz 渲染接线（SUCCESS 后生成 HTML+PNG）

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/dataviz/DataVizPostProcessor.java`
- Modify: `src/main/java/com/example/aipassagecreator/skill/SkillExecutionService.java`（settle 中仿照 comic-journal 分支增加 dataviz 分支）
- Test: `src/test/java/com/example/aipassagecreator/dataviz/DataVizPostProcessorTest.java`

**Interfaces:**
- Consumes:
  - `SkillExecutionPo`（现有 PO，含 `executionId`/`userId`）
  - `execution.getPersistedOutput()` → `Map<String,Object>`，其中 `chartSpecs` 为 AI phase2 输出（结构 `{"charts":[...]}`）
  - Task 1–5 全部组件
- Produces:
  - `DataVizPostProcessor.processAsync(SkillExecutionPo po, Map<String,Object> output)`（`@Async("skillExecutor")`）
  - 产物：`{dataDir}/dataviz/{executionId}/report.html` 与 `report.png`（dataDir 取 `user.dir` 下 `data/dataviz/`，与现有落盘模式一致；如项目已有统一产物目录配置，以它为准并在实现时对齐）
  - 渲染失败不抛出——记录 warn 日志并跳过 PNG（HTML 仍保留），保证 Skill 主流程不受影响（与 `comicJournalService.processAsync` 同样的静默降级约定）
- 接线位置：`SkillExecutionService.settle` 的 SUCCESS 分支，模式照抄 comic-journal：

```java
// dataviz 追加图表报告产物（RAG 保留无害，静默降级）
if ("data-visualization-report".equals(execution.getDefinition().getName())) {
    dataVizPostProcessor.processAsync(po, execution.getPersistedOutput());
}
```

（`dataVizPostProcessor` 以 `private final` 加入构造器注入。）

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DataVizPostProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void process_validSpecs_writesHtmlAndPng() throws Exception {
        DataVizPostProcessor p = new DataVizPostProcessor(
                new DatasetParser(), new DatasetValidator(),
                new ChartSpecValidator(), new StatsService(), new ChartHtmlRenderer(),
                tempDir.toString());
        Map<String, Object> chart = Map.of(
                "chartType", "bar",
                "style", "glance",
                "title", "阅读量排名",
                "source", "测试",
                "insightId", "i1",
                "evidence", List.of("i1"),
                "encoding", Map.of("x", "title", "y", "views"));
        Map<String, Object> output = Map.of(
                "rawData", "[{\"title\":\"A\",\"views\":100},{\"title\":\"B\",\"views\":200}]",
                "dataFormat", "json",
                "chartSpecs", Map.of("charts", List.of(chart)));

        p.process(tempDir, "exec-1", output);

        Path html = tempDir.resolve("dataviz/exec-1/report.html");
        assertTrue(Files.exists(html));
        String content = Files.readString(html);
        assertTrue(content.contains("阅读量排名"));
        assertFalse(content.toLowerCase().contains("<script"));
    }

    @Test
    void process_invalidSpec_skipsChartDoesNotThrow() {
        DataVizPostProcessor p = new DataVizPostProcessor(
                new DatasetParser(), new DatasetValidator(),
                new ChartSpecValidator(), new StatsService(), new ChartHtmlRenderer(),
                tempDir.toString());
        Map<String, Object> chart = Map.of(
                "chartType", "sankey", // 非白名单
                "title", "t", "evidence", List.of("i1"),
                "encoding", Map.of("x", "a", "y", "b"));
        Map<String, Object> output = Map.of(
                "rawData", "a,b\n1,2\n", "dataFormat", "csv",
                "chartSpecs", Map.of("charts", List.of(chart)));

        assertDoesNotThrow(() -> p.process(tempDir, "exec-2", output));
    }

    @Test
    void process_missingRawData_doesNotThrow() {
        DataVizPostProcessor p = new DataVizPostProcessor(
                new DatasetParser(), new DatasetValidator(),
                new ChartSpecValidator(), new StatsService(), new ChartHtmlRenderer(),
                tempDir.toString());
        assertDoesNotThrow(() -> p.process(tempDir, "exec-3", Map.of("chartSpecs", Map.of())));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=DataVizPostProcessorTest`
Expected: 编译失败

- [ ] **Step 3: 最小实现**

```java
package com.example.aipassagecreator.dataviz;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * dataviz Skill 收尾：解析原始数据 → 校验 Chart Spec → 渲染 HTML（+Playwright PNG）。
 * 静默降级：任何失败只记日志，不影响 Skill 主流程（与 comic-journal 同约定）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataVizPostProcessor {

    private final DatasetParser datasetParser;
    private final DatasetValidator datasetValidator;
    private final ChartSpecValidator chartSpecValidator;
    private final StatsService statsService;
    private final ChartHtmlRenderer chartHtmlRenderer;
    private final com.example.aipassagecreator.card.CardRenderPipeline cardRenderPipeline;

    @Async("skillExecutor")
    public void processAsync(com.example.aipassagecreator.model.po.SkillExecutionPo po,
                             Map<String, Object> output) {
        try {
            Path dir = Path.of(System.getProperty("user.dir"), "data", "dataviz",
                    po.getExecutionId());
            process(dir, po.getExecutionId(), output);
        } catch (Exception e) {
            log.warn("dataviz 报告生成失败（不影响 Skill 主流程）: executionId={}",
                    po.getExecutionId(), e);
        }
    }

    /** 供异步入口与测试共用；目录由调用方给定 */
    public void process(Path dir, String executionId, Map<String, Object> output) {
        if (output == null || output.isEmpty()) return;
        Object raw = output.get("rawData");
        Object fmt = output.get("dataFormat");
        if (raw == null || fmt == null) {
            log.warn("dataviz 输出缺 rawData/dataFormat，跳过: executionId={}", executionId);
            return;
        }
        Dataset ds = datasetParser.parse(fmt.toString(), raw.toString());
        DataQualityReport profile = datasetValidator.validate(ds);

        List<ChartSpec> specs = extractSpecs(output.get("chartSpecs"));
        List<String> fragments = new ArrayList<>();
        for (ChartSpec spec : specs) {
            try {
                chartSpecValidator.validate(spec, ds, profile);
                fragments.add(chartHtmlRenderer.renderChart(spec, ds, profile));
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

        List<String> warnings = profile.warnings();
        String html = chartHtmlRenderer.renderPage("数据图表报告", fragments, warnings);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("report.html"), html);
        } catch (Exception e) {
            log.warn("dataviz HTML 落盘失败: executionId={}", executionId, e);
            return;
        }

        // PNG：渲染引擎不可用时静默跳过（本地无浏览器/CI）
        if (cardRenderPipeline.isHealthy()) {
            try {
                var results = cardRenderPipeline.renderWithJs(
                        List.of(html), executionId, handwritingConfig());
                if (!results.isEmpty() && results.get(0).getPngBytes() != null) {
                    Files.write(dir.resolve("report.png"), results.get(0).getPngBytes());
                }
            } catch (Exception e) {
                log.warn("dataviz PNG 导出失败（HTML 已保留）: executionId={}", executionId, e);
            }
        } else {
            log.info("渲染引擎不可用，dataviz 跳过 PNG: executionId={}", executionId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ChartSpec> extractSpecs(Object chartSpecsObj) {
        if (!(chartSpecsObj instanceof Map<?, ?> wrapper)
                || !(wrapper.get("charts") instanceof List<?> list)) {
            return List.of();
        }
        ObjectMapper mapper = new ObjectMapper();
        return list.stream()
                .filter(item -> item instanceof Map)
                .map(item -> mapper.convertValue(item, ChartSpec.class))
                .collect(Collectors.toList());
    }
}
```

实现说明：
- `CardRenderPipeline`、`HandwritingRenderConfig` 以现有类直接复用；`renderWithJs` 启用 JS 不是必须（我们的 HTML 无 JS），但 `render(List,String)` 视口为手机竖屏 1080×1920 且固定 `setJavaScriptEnabled(false)`，对图表报告可用——**优先用 `render(List, String)`**（禁 JS 更安全）；仅当布局探针对长表格误报溢出时，再考虑 `renderWithJs` + 自定义视口。实现时先选 `render(List, String)`，其 `taskId` 参数传 `executionId`。
- 构造器注入 `CardRenderPipeline`（改为 `private final` 字段加入 `@RequiredArgsConstructor`），测试中传 `null` 或构造一个 `healthy=false` 的实例——为让测试不依赖 Playwright，`process(Path, ...)` 重载接受 `CardRenderPipeline` 为 null：null 时直接跳过 PNG 分支。上测试代码相应把构造参数补齐为 `(parser, validator, specValidator, stats, renderer, null, tempDir)`，实现里 `if (cardRenderPipeline != null && cardRenderPipeline.isHealthy())`。

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -Dtest=DataVizPostProcessorTest`
Expected: PASS (3 tests)

- [ ] **Step 5: 接线 SkillExecutionService**

`SkillExecutionService.java` 修改点：
1. 字段区增加 `private final DataVizPostProcessor dataVizPostProcessor;`
2. `settle(...)` 中 SUCCESS 分支、comic-journal 判断之后追加：

```java
// dataviz 追加图表报告产物（RAG 保留无害，静默降级）
if ("data-visualization-report".equals(execution.getDefinition().getName())) {
    if (po != null) {
        dataVizPostProcessor.processAsync(po, execution.getPersistedOutput());
    }
}
```

- [ ] **Step 6: 全量回归**

Run: `mvn test`
Expected: 全绿（若 `DataVizPostProcessor` 构造器注入导致其他 `@SpringBootTest` 装配失败，检查 `CardRenderPipeline` 是否已为 Spring Bean——它是 `@Component`，可注入）

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/dataviz/ src/main/java/com/example/aipassagecreator/skill/SkillExecutionService.java src/test/java/com/example/aipassagecreator/dataviz/DataVizPostProcessorTest.java
git commit -m "feat(dataviz): Skill SUCCESS 后生成图表报告 HTML/PNG（静默降级）"
```

---

### Task 8: 报告产物查询 API

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/dataviz/DataVizController.java`
- Create: `src/main/java/com/example/aipassagecreator/dataviz/DataVizArtifactVO.java`
- Test: `src/test/java/com/example/aipassagecreator/dataviz/DataVizControllerTest.java`

**Interfaces:**
- Consumes: Task 7 落盘的 `{user.dir}/data/dataviz/{executionId}/report.html|png`；`SkillExecutionMapper`（查 executionId → userId 归属）
- Produces:
  - `GET /dataviz/{executionId}/artifact` → `BaseResponse<DataVizArtifactVO>`
  - `record DataVizArtifactVO(boolean htmlReady, boolean pngReady, String htmlUrl, String pngUrl)`
  - `GET /dataviz/{executionId}/html` → 直接回 `text/html`（`ResponseEntity<byte[]>`）
  - `GET /dataviz/{executionId}/png` → 直接回 PNG
  - 安全：`executionId` 仅接受 UUID 正则 `[0-9a-fA-F-]{36}`（防路径穿越）；先查 `SkillExecutionPo.userId` 与当前登录用户一致，不一致返回 `"无权访问该报告"`（403 语义经 `BusinessException`，沿用项目全局异常处理器）；admin 放行
  - 控制器保持薄：只校验+派发，文件读取逻辑放新建的 `DataVizStorageService`（Create: `src/main/java/com/example/aipassagecreator/dataviz/DataVizStorageService.java`，`Path artifactDir(String executionId)` / `boolean exists(Path, String fileName)` / `byte[] read(Path, String fileName)`）

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DataVizStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void artifactDir_rejectsPathTraversal() {
        DataVizStorageService s = new DataVizStorageService();
        assertThrows(IllegalArgumentException.class,
                () -> s.artifactDir("../../etc"));
        assertThrows(IllegalArgumentException.class,
                () -> s.artifactDir("exec/../../escape"));
    }

    @Test
    void artifactDir_acceptsUuid() {
        String id = "550e8400-e29b-41d4-a716-446655440000";
        Path p = new DataVizStorageService().artifactDir(id);
        assertTrue(p.toString().endsWith(id));
    }

    @Test
    void read_missingFile_returnsNull() throws Exception {
        DataVizStorageService s = new DataVizStorageService();
        Path dir = s.artifactDir("550e8400-e29b-41d4-a716-446655440000");
        Files.createDirectories(dir);
        assertNull(s.read(dir, "nope.html"));
    }
}
```

（MockMvc 层面鉴权测试沿用项目既有 Controller 测试模式；此处只对纯逻辑 StorageService 做单测，鉴权逻辑在 Controller 中通过 `userService.getLoginUser(request)` + `SkillExecutionMapper.selectOneById` 组合，集成验证放到后续 E2E。）

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -Dtest=DataVizStorageServiceTest`
Expected: 编译失败

- [ ] **Step 3: 最小实现**

```java
package com.example.aipassagecreator.dataviz;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** dataviz 产物文件访问：路径白名单校验，防目录穿越 */
@Component
public class DataVizStorageService {

    private static final Pattern EXEC_ID = Pattern.compile("[0-9a-fA-F-]{36}");

    public Path artifactDir(String executionId) {
        if (executionId == null || !EXEC_ID.matcher(executionId).matches()) {
            throw new IllegalArgumentException("非法的报告标识");
        }
        return Path.of(System.getProperty("user.dir"), "data", "dataviz", executionId);
    }

    public boolean exists(Path dir, String fileName) {
        return Files.isRegularFile(dir.resolve(fileName));
    }

    public byte[] read(Path dir, String fileName) {
        try {
            Path f = dir.resolve(fileName);
            if (!Files.isRegularFile(f)) return null;
            return Files.readAllBytes(f);
        } catch (Exception e) {
            return null;
        }
    }
}
```

Controller（薄层，鉴权+派发）：

```java
package com.example.aipassagecreator.dataviz;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

/** 数据图表报告产物查询。产物由 DataVizPostProcessor 异步生成，可能尚未就绪 */
@RestController
@RequestMapping("/dataviz")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "DataVizController", description = "数据图表报告")
public class DataVizController {

    private final DataVizStorageService storage;
    private final UserService userService;
    private final com.example.aipassagecreator.mapper.SkillExecutionMapper executionMapper;
    private final jakarta.servlet.http.HttpServletRequest request; // 见下方说明

    // 说明：项目既有 Controller 惯例为 @Resource 字段注入 UserService、
    // 方法参数 HttpServletRequest；本类按构造器注入新代码规范实现，
    // HttpServletRequest 改为方法参数注入（实现时以 AnalyticsController 为准微调）。

    @GetMapping("/{executionId}/artifact")
    @Operation(summary = "查询报告产物就绪状态")
    public BaseResponse<DataVizArtifactVO> getArtifact(@PathVariable String executionId) {
        SkillExecutionPo po = ownedExecution(executionId);
        Path dir = storage.artifactDir(executionId);
        return ResultUtils.success(new DataVizArtifactVO(
                storage.exists(dir, "report.html"),
                storage.exists(dir, "report.png"),
                "/api/dataviz/" + executionId + "/html",
                "/api/dataviz/" + executionId + "/png"));
    }

    @GetMapping("/{executionId}/html")
    @Operation(summary = "报告 HTML")
    public ResponseEntity<byte[]> getHtml(@PathVariable String executionId) {
        ownedExecution(executionId);
        byte[] body = storage.read(storage.artifactDir(executionId), "report.html");
        if (body == null) throw new BusinessException("报告尚未生成，请稍后刷新");
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
    }

    @GetMapping("/{executionId}/png")
    @Operation(summary = "报告 PNG")
    public ResponseEntity<byte[]> getPng(@PathVariable String executionId) {
        ownedExecution(executionId);
        byte[] body = storage.read(storage.artifactDir(executionId), "report.png");
        if (body == null) throw new BusinessException("PNG 尚未导出或渲染引擎不可用");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(body);
    }

    private SkillExecutionPo ownedExecution(String executionId) {
        SkillExecutionPo po = executionMapper.selectOneById(executionId);
        if (po == null) throw new BusinessException("报告不存在");
        User loginUser = userService.getLoginUser(request);
        if (!"admin".equals(loginUser.getUserRole())
                && !loginUser.getId().equals(po.getUserId())) {
            throw new BusinessException("无权访问该报告");
        }
        return po;
    }
}
```

```java
package com.example.aipassagecreator.dataviz;

/** 报告产物就绪状态 */
public record DataVizArtifactVO(
        boolean htmlReady,
        boolean pngReady,
        String htmlUrl,
        String pngUrl) {
}
```

实现时注意事项（执行者必读）：
1. `HttpServletRequest` 不放入构造器——改为每个方法参数 `HttpServletRequest request` 传入，`ownedExecution(executionId, request)` 双参；以 `AnalyticsController` 现有写法为准。
2. `BusinessException` 的实际类名/构造签名以 `com.example.aipassagecreator.exception` 包现有类为准；若项目用 `BusinessException(ErrorCode, msg)` 双参，按现有 Controller 抛错写法对齐。
3. `executionMapper.selectOneById` 的主键类型以 `SkillExecutionPo` 实际 `@Id` 字段类型为准（若为 String 直传；若为其他类型需转换）。

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -Dtest=DataVizStorageServiceTest`
Expected: PASS (3 tests)

- [ ] **Step 5: 全量回归**

Run: `mvn test`
Expected: 全绿

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/dataviz/ src/test/java/com/example/aipassagecreator/dataviz/DataVizStorageServiceTest.java
git commit -m "feat(dataviz): 报告产物查询 API（归属校验+路径白名单）"
```

---

### Task 9: 端到端验收与文档

**Files:**
- Modify: `CLAUDE.md`（架构图增加 dataviz 数据流 + 路由表不变——本期无前端页面）
- Create: `docs/data-visualization-report-phase1-delivery.md`

**Interfaces:**
- Consumes: Task 1–8 全部产物
- Produces: 验收记录文档

- [ ] **Step 1: 后端全量测试**

Run: `mvn test`
Expected: 全绿。若有失败，修复后再继续，不允许带病提交。

- [ ] **Step 2: 手动冒烟（本地有浏览器环境时）**

```bash
mvn spring-boot:run
# 登录后通过 SkillExecutePage 或 curl 执行：
# POST /api/skill/data-visualization-report/execute
# inputs: {"rawData": "month,revenue\n2026-01,100\n2026-02,150\n2026-03,120", "dataFormat": "csv", "goal": "看收入趋势", "style": "glance"}
# 确认后 GET /api/dataviz/{executionId}/artifact 直到 htmlReady=true
# 浏览器打开 /api/dataviz/{executionId}/html 目视检查图表
```

Expected: HTML 有折线/柱状 SVG；`playwright.enabled=false` 时 PNG 分支 info 日志跳过、不报错。

- [ ] **Step 3: 写交付文档**

`docs/data-visualization-report-phase1-delivery.md` 记录：新增文件清单、API 契约、已知限制（无前端工作台、无 PDF/RAG/回流——对应 Phase 2+）、测试结果原文、后续计划。

- [ ] **Step 4: Commit**

```bash
git add docs/data-visualization-report-phase1-delivery.md CLAUDE.md
git commit -m "docs(dataviz): Phase 1 交付记录与架构图更新"
```

---

## Self-Review 记录

1. **Spec coverage**：设计文档 Phase 1 承诺的 Skill YAML、解析、校验、六图型、三风格、三模板、PNG/PDF——本计划按 Phase 0 收敛决策只做 line/bar/table + HTML/PNG，pie/scatter/area/三模板/PDF 属于 Phase 2+，与 `docs/data-visualization-report-phase0.md` 第 7 节确认事项一致。缺口：无。
2. **Placeholder scan**：Task 6 测试对 `VariableDef.getOptions()` 返回类型的说明、Task 8 对 `BusinessException` 签名的"以现有类为准"指引——这些是防止实现者与既有代码不一致的核对项而非 TBD；核心代码均给出完整实现。
3. **Type consistency**：`Dataset(rows, headers)`、`ChartSpec.Encoding(x,y,color)`、`ChartSpecValidator.validate(spec, ds, profile)`、`ChartHtmlRenderer.renderChart/renderPage`、`DataVizPostProcessor.process(Path, String, Map)` 各任务间签名一致；`rankBy` 重复实现已在 Task 4 内标注只保留单一版本。
