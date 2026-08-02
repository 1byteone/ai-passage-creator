package com.example.aipassagecreator.methodology.antiai;

import java.util.List;

/**
 * 去AI味检测器：对文章内容进行 AI 味扫描，输出合规报告。
 */
public class AntiAiFlavorChecker {

    public static class AiFlavorReport {
        private final List<String> violations;
        private final boolean passed;
        private final int score; // 0-100, 越高越像真人

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
     * 检测文本的 AI 味程度。
     * <p>评分规则：基础分 100，每处违规扣 10 分，违规 ≥ 5 项判定不通过。</p>
     */
    public static AiFlavorReport check(String text) {
        List<String> violations = AntiAiFlavorRules.detect(text);
        int score = Math.max(0, 100 - violations.size() * 10);
        boolean passed = violations.size() < 5;
        return new AiFlavorReport(violations, passed, score);
    }
}