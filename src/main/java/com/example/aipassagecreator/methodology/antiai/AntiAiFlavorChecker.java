package com.example.aipassagecreator.methodology.antiai;

import java.util.List;

/**
 * 表达质量检查器：对文章内容进行质量风险扫描，输出可解释报告。
 */
public class AntiAiFlavorChecker {

    public static class AiFlavorReport {
        private final List<String> violations;
        private final boolean passed;
        private final int score; // 0-100, 越高表示命中风险越少

        public AiFlavorReport(List<String> violations, boolean passed, int score) {
            this.violations = violations;
            this.passed = passed;
            this.score = score;
        }

        public List<String> violations() { return violations; }
        public boolean passed() { return passed; }
        public int score() { return score; }
        public boolean hasViolations() { return violations != null && !violations.isEmpty(); }
    }

    /**
     * 检测文本的表达质量风险。
     * <p>评分规则：基础分 100，每处风险扣 10 分；通过与否由质量门阈值决定。</p>
     */
    public static AiFlavorReport check(String text) {
        return check(text, 50);
    }

    /**
     * 按质量门配置的阈值判定。
     *
     * @param passThreshold 通过阈值，自动限制在 0-100
     */
    public static AiFlavorReport check(String text, int passThreshold) {
        List<String> violations = AntiAiFlavorRules.detect(text);
        int score = Math.max(0, 100 - violations.size() * 10);
        int threshold = Math.max(0, Math.min(100, passThreshold));
        boolean passed = score >= threshold;
        return new AiFlavorReport(violations, passed, score);
    }
}
