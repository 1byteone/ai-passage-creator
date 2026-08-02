package com.example.aipassagecreator.publish.platform;

import com.example.aipassagecreator.methodology.MethodologyDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 小红书平台适配器：Markdown → 纯文本 + emoji + 话题标签。
 * <p>小红书风格：短句、emoji 引导、友聊天、"合集"话题。</p>
 */
@Component
public class XiaohongshuAdapter implements PlatformAdapter {

    private static final Pattern MARKDOWN_TAG = Pattern.compile("[*#_>`\\[\\]()!]");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+");

    @Override
    public String platform() {
        return "xiaohongshu";
    }

    @Override
    public PlatformContent convert(String title, String markdown,
                                   MethodologyDefinition def) {
        // Markdown → 纯文本：去标记 + 去空段 + emoji 点缀
        String text = markdown;
        // 图片替换为 emoji 占位
        text = text.replaceAll("!\\[.*?\\]\\(.*?\\)", "📷");
        // 加粗转 emoji 强调
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "✨$1✨");
        // 章节标题加 emoji 前缀
        text = HEADING.matcher(text).replaceAll("📌 ");
        // 去剩余标记
        text = MARKDOWN_TAG.matcher(text).replaceAll("");
        // 清理多余空行
        text = text.replaceAll("\\n{3,}", "\n\n").trim();

        // 从方法论或大纲提取话题标签
        List<String> topics = extractTopics(markdown, def);

        int minChars = def.getPlatform() != null && def.getPlatform().getMinChars() != null
                ? def.getPlatform().getMinChars() : 300;
        int maxChars = def.getPlatform() != null && def.getPlatform().getMaxChars() != null
                ? def.getPlatform().getMaxChars() : 800;

        Map<String, Object> meta = Map.of(
                "platform", "xiaohongshu",
                "format", "plaintext",
                "minChars", minChars, "maxChars", maxChars,
                "charCount", text.replaceAll("\\s", "").length(),
                "topicCount", topics.size()
        );
        return new PlatformContent(title, text, topics, meta);
    }

    @Override
    public List<ComplianceIssue> validate(PlatformContent content, MethodologyDefinition def) {
        List<ComplianceIssue> issues = new ArrayList<>();
        int minChars = def.getPlatform() != null && def.getPlatform().getMinChars() != null
                ? def.getPlatform().getMinChars() : 300;
        int maxChars = def.getPlatform() != null && def.getPlatform().getMaxChars() != null
                ? def.getPlatform().getMaxChars() : 800;

        int len = content.body() != null ? content.body().length() : 0;
        if (len < minChars) {
            issues.add(new ComplianceIssue("LengthRule", "WARNING",
                    "小红书建议 ≥" + minChars + "字，当前 " + len + "字"));
        }
        if (len > maxChars) {
            issues.add(new ComplianceIssue("LengthRule", "ERROR",
                    "小红书上限 " + maxChars + "字，当前 " + len + "字"));
        }
        if (content.topics() == null || content.topics().size() < 5) {
            issues.add(new ComplianceIssue("TopicCountRule", "WARNING",
                    "建议 ≥5 个话题标签，当前 " +
                            (content.topics() != null ? content.topics().size() : 0) + "个"));
        }
        return issues;
    }

    private List<String> extractTopics(String markdown, MethodologyDefinition def) {
        List<String> topics = new ArrayList<>();
        // 从 ## 标题提取话题（去重，防止重复章节标题产出重复话题）
        String[] lines = markdown.split("\n");
        for (String line : lines) {
            if (line.startsWith("## ") && line.length() > 3) {
                String topic = line.substring(3).trim();
                if (!topic.isEmpty() && !topics.contains(topic) && topics.size() < 5) {
                    topics.add(topic);
                }
            }
        }
        // 从方法论创作维度提取
        if (def.getCreationDimensions() != null) {
            def.getCreationDimensions().stream()
                    .limit(3)
                    .map(d -> d.getName())
                    .filter(n -> !topics.contains(n))
                    .forEach(topics::add);
        }
        return topics;
    }
}
