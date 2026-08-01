package com.example.aipassagecreator.methodology;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 方法论模板的 Java 映射。
 * <p>注意：YAML 的 {@code parent} 字段对应继承父模板（设计文档中为 extends，因 Java 关键字改用 parent）。</p>
 */
@Data
public class MethodologyDefinition {

    private String name;
    private String description;
    private String version;

    /** 父模板名称（继承 default），YAML 键为 parent */
    private String parent;

    private List<CreationDimension> creationDimensions;
    private List<TitleStrategy> titleStrategies;
    private List<EvaluationDimension> evaluationDimensions;
    private PlatformConfig platform;

    @Data
    public static class CreationDimension {
        private String key;
        private String name;
        private String guidance;
    }

    @Data
    public static class TitleStrategy {
        private String key;
        private String name;
    }

    @Data
    public static class EvaluationDimension {
        private String key;
        private String name;
        private Integer weight;
        private String rubric;
    }

    @Data
    public static class PlatformConfig {
        private String name;
        private String audience;
        private Integer minChars;
        private Integer maxChars;
        private String style;
        /** 卡片风格 (warm/minimal/free)，默认 warm */
        private String cardStyle;
        /** 平台覆盖的评测维度权重（按 key patch） */
        private Map<String, Integer> evaluationWeights;
    }
}
