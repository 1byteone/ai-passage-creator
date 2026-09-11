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
