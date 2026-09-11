package com.example.aipassagecreator.dataviz;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        rows.sort((a, b) -> Double.compare(toDouble(b.get(measureField)), toDouble(a.get(measureField))));
        return rows.stream().limit(topN).toList();
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
