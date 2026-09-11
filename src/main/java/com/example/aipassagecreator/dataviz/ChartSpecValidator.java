package com.example.aipassagecreator.dataviz;

import org.springframework.stereotype.Component;

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
