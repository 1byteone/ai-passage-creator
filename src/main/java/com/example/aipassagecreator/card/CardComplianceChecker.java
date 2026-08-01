package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.ComplianceReport;
import com.example.aipassagecreator.card.model.PagePlan;
import com.example.aipassagecreator.methodology.MethodologyDefinition;
import com.example.aipassagecreator.methodology.MethodologyRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 卡片合规检查器（文本层规则）。
 * <p>pre-render 阶段执行，ERROR 级不通过应阻断生成。</p>
 * <p>规则：
 * <ul>
 *   <li>TitleLengthRule（WARNING）：主标题 ≤ 20 字；</li>
 *   <li>ContentLengthRule（ERROR）：每页内容 ≤ 平台 maxChars（来自 MethodologyRegistry）；</li>
 *   <li>PlaceholderRule（ERROR）：内容不得残留 {{IMAGE|ICON_PLACEHOLDER_\\d}} 占位符。</li>
 * </ul></p>
 */
@Slf4j
@Component
public class CardComplianceChecker {

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{\\s*(IMAGE|ICON)_PLACEHOLDER_\\d+\\s*\\}\\}");

    /** 主标题建议长度上限 */
    private static final int MAX_TITLE_LEN = 20;

    /** 平台未配置 maxChars 时的兜底值 */
    private static final int DEFAULT_MAX_CHARS = 1000;

    private final MethodologyRegistry methodologyRegistry;

    public CardComplianceChecker(MethodologyRegistry methodologyRegistry) {
        this.methodologyRegistry = methodologyRegistry;
    }

    /**
     * 文本层合规检查（pre-render）。返回报告，error 级不通过应阻断生成。
     *
     * @param pages           分页方案
     * @param mainTitle       主标题
     * @param methodologyName 方法论名称（读取 platform.maxChars）
     */
    public ComplianceReport textCheck(List<PagePlan> pages, String mainTitle,
                                      String methodologyName) {
        List<ComplianceReport.RuleResult> results = new ArrayList<>();
        // 平台阈值
        MethodologyDefinition def = methodologyRegistry.get(methodologyName);
        Integer maxChars = DEFAULT_MAX_CHARS;
        if (def.getPlatform() != null && def.getPlatform().getMaxChars() != null) {
            maxChars = def.getPlatform().getMaxChars();
        }

        // 规则 1: 标题长度
        boolean titleOk = mainTitle == null || mainTitle.length() <= MAX_TITLE_LEN;
        results.add(new ComplianceReport.RuleResult("TitleLengthRule",
                "WARNING", titleOk,
                titleOk ? "通过" : "标题超" + mainTitle.length() + "字(建议≤" + MAX_TITLE_LEN + ")",
                null));

        // 规则 2: 每页内容长度
        for (PagePlan page : pages) {
            int len = page.getContentMd().length();
            boolean ok = len <= maxChars;
            results.add(new ComplianceReport.RuleResult("ContentLengthRule",
                    "ERROR", ok,
                    ok ? "通过" : "第" + page.getPageNo() + "页超" + len + "字(上限" + maxChars + ")",
                    page.getPageNo()));
        }

        // 规则 3: 残留占位符
        for (PagePlan page : pages) {
            boolean ok = !PLACEHOLDER_PATTERN.matcher(page.getContentMd()).find();
            if (!ok) {
                results.add(new ComplianceReport.RuleResult("PlaceholderRule",
                        "ERROR", false,
                        "第" + page.getPageNo() + "页含残留占位符", page.getPageNo()));
            }
        }

        boolean passed = results.stream()
                .filter(r -> "ERROR".equals(r.getLevel()))
                .allMatch(r -> r.isPassed());
        return ComplianceReport.builder().passed(passed).rules(results).build();
    }
}
