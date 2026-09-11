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
        return "json".equals(fmt) ? parseJson(rawData) : parseCsv(rawData);
    }

    private Dataset parseJson(String raw) {
        List<Map<String, Object>> rawRows;
        try {
            rawRows = mapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            throw new DatasetParseException("JSON 格式不正确，需为对象数组");
        }
        if (rawRows == null || rawRows.isEmpty()) {
            throw new DatasetParseException("JSON 格式不正确，需为对象数组");
        }
        if (rawRows.size() > MAX_ROWS) {
            throw new DatasetParseException("行数超过 1000 上限，请精简后再试");
        }

        // 合并所有行的 keySet（保序 LinkedHashSet）作为 headers
        Set<String> allHeaders = new LinkedHashSet<>();
        for (Map<String, Object> rawRow : rawRows) {
            if (rawRow != null) {
                rawRow.keySet().forEach(k -> {
                    String key = k == null ? "" : k.trim();
                    if (!key.isEmpty()) {
                        allHeaders.add(key);
                    }
                });
            }
        }
        if (allHeaders.size() > MAX_COLS) {
            throw new DatasetParseException("列数超过 50 上限，请精简后再试");
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, Object> rawRow : rawRows) {
            if (rawRow == null) continue;
            Set<String> seen = new LinkedHashSet<>();
            Map<String, String> row = new LinkedHashMap<>();
            rawRow.forEach((k, v) -> {
                String key = k == null ? "" : k.trim();
                if (key.isEmpty() || !seen.add(key)) return;
                String value = valueToString(rawRow.get(k));
                if (value.length() > MAX_CELL) {
                    throw new DatasetParseException("单元格超过 200 字符上限: 列 " + key);
                }
                row.put(key, value);
            });
            if (!row.isEmpty()) rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new DatasetParseException("未解析到有效数据行");
        }
        return new Dataset(rows, new ArrayList<>(allHeaders));
    }

    private Dataset parseCsv(String raw) {
        // hutool CsvReader 已带引号处理，这里直接使用
        var reader = cn.hutool.core.text.csv.CsvUtil.getReader();
        List<cn.hutool.core.text.csv.CsvRow> csvRows = reader.readFromStr(raw).getRows();
        if (csvRows.isEmpty()) {
            throw new DatasetParseException("未解析到有效数据行");
        }
        int rawColCount = csvRows.get(0).size();
        if (rawColCount > MAX_COLS) {
            throw new DatasetParseException("列数超过 50 上限，请精简后再试");
        }
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < rawColCount; i++) {
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
        return new Dataset(rows, headers);
    }

    private String valueToString(Object v) {
        if (v == null) return "";
        if (v instanceof Double || v instanceof Float || v instanceof java.math.BigDecimal) {
            double d = ((Number) v).doubleValue();
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
        return v.toString();
    }
}
