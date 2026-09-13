package com.example.aipassagecreator.agent;

import java.util.List;
import java.util.Map;

/** 关键词→skill 意图检测（spec §3.5 规则兜底；LLM 判定为后续增强项） */
public final class AgentSkillIntentDetector {

    private static final Map<String, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry("content-summarizer", List.of("总结", "摘要", "概括")),
            Map.entry("rewrite-plagiarism", List.of("改写", "降重", "重写")),
            Map.entry("topic-gen", List.of("选题", "灵感", "想写")),
            Map.entry("headline-optimizer", List.of("标题")),
            Map.entry("outline-expander", List.of("大纲", "扩展章节")),
            Map.entry("content-translator", List.of("翻译", "译成")),
            Map.entry("video-script", List.of("脚本", "短视频")),
            Map.entry("research", List.of("调研", "研究", "搜集资料")),
            Map.entry("seeding-copy", List.of("种草", "带货文案")),
            Map.entry("seo-optimizer", List.of("seo", "搜索引擎优化")),
            Map.entry("proofreading", List.of("审校", "校对")),
            Map.entry("ai-detox", List.of("去ai味", "降ai检测", "ai味", "表达优化", "内容质量优化", "自然表达")),
            Map.entry("article-to-x", List.of("社交文案", "浓缩成", "长文浓缩")));

    private AgentSkillIntentDetector() {
    }

    /** 命中关键词返回 skill 名（大小写不敏感），否则返回 null */
    public static String detect(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String lower = message.toLowerCase();
        for (Map.Entry<String, List<String>> entry : KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
