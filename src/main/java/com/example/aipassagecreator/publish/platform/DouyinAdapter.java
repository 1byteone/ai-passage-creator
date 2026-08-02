package com.example.aipassagecreator.publish.platform;

import com.example.aipassagecreator.methodology.MethodologyDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 抖音平台适配器：Markdown → 口播短文案。
 * <p>取首段作为"前3秒钩子"，摘取核心观点（## 标题）作为正文。</p>
 */
@Component
public class DouyinAdapter implements PlatformAdapter {

    private static final Pattern MARKDOWN_TAG = Pattern.compile("[*#_>`\\[\\]()!]");

    @Override
    public String platform() {
        return "douyin";
    }

    @Override
    public PlatformContent convert(String title, String markdown,
                                   MethodologyDefinition def) {
        // 去除 Markdown 标记
        String text = MARKDOWN_TAG.matcher(markdown).replaceAll("");
        text = text.replaceAll("\\n{3,}", "\n").trim();

        // 首段作为钩子（前 3 秒）
        String[] paras = text.split("\n\n", 2);
        String hook = paras.length > 0 ? limitChars(paras[0].trim(), 120) : "";
        String body = paras.length > 1 ? limitChars(paras[1].trim(), 400) : limitChars(text, 400);

        // 拼接口播风格：(钩子) + 正文 + CTA
        String content = hook;
        if (!body.isEmpty()) content += "\n\n" + body;
        content += "\n\n#创作灵感 #干货分享";

        int minChars = def.getPlatform() != null && def.getPlatform().getMinChars() != null
                ? def.getPlatform().getMinChars() : 200;
        int maxChars = def.getPlatform() != null && def.getPlatform().getMaxChars() != null
                ? def.getPlatform().getMaxChars() : 500;

        Map<String, Object> meta = Map.of(
                "platform", "douyin",
                "format", "plaintext",
                "minChars", minChars, "maxChars", maxChars,
                "charCount", content.replaceAll("\\s", "").length(),
                "hasHook", !hook.isEmpty()
        );
        return new PlatformContent(title, content, List.of(), meta);
    }

    @Override
    public List<ComplianceIssue> validate(PlatformContent content, MethodologyDefinition def) {
        List<ComplianceIssue> issues = new ArrayList<>();
        int minChars = def.getPlatform() != null && def.getPlatform().getMinChars() != null
                ? def.getPlatform().getMinChars() : 200;
        int maxChars = def.getPlatform() != null && def.getPlatform().getMaxChars() != null
                ? def.getPlatform().getMaxChars() : 500;

        int len = content.body() != null ? content.body().length() : 0;
        if (len < minChars) {
            issues.add(new ComplianceIssue("LengthRule", "WARNING",
                    "抖音建议 ≥" + minChars + "字，当前 " + len + "字"));
        }
        if (len > maxChars) {
            issues.add(new ComplianceIssue("LengthRule", "ERROR",
                    "抖音上限 " + maxChars + "字，当前 " + len + "字"));
        }
        if (content.body() == null || !content.body().contains("\n\n")) {
            issues.add(new ComplianceIssue("HookRule", "WARNING",
                    "建议含明显的钩子段+内容段分隔"));
        }
        return issues;
    }

    private String limitChars(String s, int max) {
        if (s.length() <= max) return s;
        int cut = s.lastIndexOf('。', max);
        return cut > 0 ? s.substring(0, cut + 1) : s.substring(0, max) + "...";
    }
}
