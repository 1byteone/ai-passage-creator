package com.example.aipassagecreator.publish.platform;

import com.example.aipassagecreator.methodology.MethodologyDefinition;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 公众号平台适配器：Markdown → 富文本 HTML。
 * <p>保留段落/加粗/引用块结构，适合公众号编辑器粘贴。</p>
 */
@Component
public class WechatAdapter implements PlatformAdapter {

    private static final Pattern IMAGE_PLACEHOLDER = Pattern.compile("!\\[.*?\\]\\(.*?\\)");

    private final Parser mdParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    @Override
    public String platform() {
        return "wechat";
    }

    @Override
    public PlatformContent convert(String title, String markdown,
                                   MethodologyDefinition def) {
        // 图片标签在公众号编辑器粘贴时会自动处理，保留 Markdown 语法
        String html = htmlRenderer.render(mdParser.parse(markdown));

        int minChars = def.getPlatform() != null && def.getPlatform().getMinChars() != null
                ? def.getPlatform().getMinChars() : 1500;

        Map<String, Object> meta = Map.of(
                "platform", "wechat",
                "format", "html",
                "minChars", minChars,
                "charCount", markdown.replaceAll("\\s", "").length()
        );
        return new PlatformContent(title, html, List.of(), meta);
    }

    @Override
    public List<ComplianceIssue> validate(PlatformContent content, MethodologyDefinition def) {
        List<ComplianceIssue> issues = new ArrayList<>();
        int minChars = def.getPlatform() != null && def.getPlatform().getMinChars() != null
                ? def.getPlatform().getMinChars() : 1500;
        int maxChars = def.getPlatform() != null && def.getPlatform().getMaxChars() != null
                ? def.getPlatform().getMaxChars() : 3000;

        int len = content.body() != null ? content.body().replaceAll("<[^>]+>", "").length() : 0;
        if (len < minChars) {
            issues.add(new ComplianceIssue("LengthRule", "WARNING",
                    "公众号建议 ≥" + minChars + "字，当前 " + len + "字"));
        }
        if (len > maxChars) {
            issues.add(new ComplianceIssue("LengthRule", "ERROR",
                    "公众号上限 " + maxChars + "字，当前 " + len + "字"));
        }
        return issues;
    }
}
