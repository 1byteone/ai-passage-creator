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
            // Dataset 内层 Map 不保证迭代顺序，行指纹与缺失统计一律按 headers 取列
            StringBuilder key = new StringBuilder();
            for (String h : ds.headers()) {
                String v = row.get(h);
                if (v == null || v.isBlank()) {
                    missingCells++;
                    v = "";
                }
                key.append(v).append((char) 1);
            }
            if (!rowKeys.add(key.toString())) duplicateRows++;
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

    /**
     * 存在任一可解析的非空数值即视为数值列：混合脏值列仍按 measure 画像，
     * 非法值由 hasNonNumeric 警告并按 0 参与计算（见 validate）。
     */
    private boolean isNumericColumn(Dataset ds, String col) {
        for (Map<String, String> row : ds.rows()) {
            String v = row.get(col);
            if (v == null || v.isBlank()) continue;
            try {
                Double.parseDouble(v.trim().replace(",", ""));
                return true;
            } catch (NumberFormatException e) {
                // 单值不可解析不代表整列非数值
            }
        }
        return false;
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
}
