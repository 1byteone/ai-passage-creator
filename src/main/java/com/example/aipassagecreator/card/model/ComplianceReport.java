package com.example.aipassagecreator.card.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 卡片合规检查报告。
 * <p>{@code passed} 为整体是否通过（任一 ERROR 级规则失败即不通过）；
 * {@code rules} 为逐条规则结果。</p>
 * <p>实现说明：{@code @Builder} 默认生成包私有全参构造器，而本模型在
 * {@code card} 包（{@code com.example.aipassagecreator.card}）被以
 * {@code new ComplianceReport.RuleResult(...)} 构造，因此显式添加
 * {@code @AllArgsConstructor @NoArgsConstructor} 使其可从外部包构造。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ComplianceReport {
    private boolean passed;
    private List<RuleResult> rules;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RuleResult {
        private String ruleName;
        private String level;    // ERROR / WARNING
        private boolean passed;
        private String message;
        private Integer pageNo;
    }
}
