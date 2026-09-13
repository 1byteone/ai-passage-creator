package com.example.aipassagecreator.enums;

import lombok.Getter;

@Getter
public enum SseMessageTypeEnum {

    /**
     * 智能体1完成（生成标题方案）
     */
    AGENT1_COMPLETE("AGENT1_COMPLETE", "标题方案生成完成"),

    /**
     * 标题方案生成完成（等待用户选择）
     */
    TITLES_GENERATED("TITLES_GENERATED", "标题方案已生成"),

    /**
     * 大纲生成完成（等待用户编辑）
     */
    OUTLINE_GENERATED("OUTLINE_GENERATED", "大纲已生成"),

    /**
     * 智能体2流式输出（大纲）
     */
    AGENT2_STREAMING("AGENT2_STREAMING", "大纲流式输出"),

    /**
     * 智能体2完成（生成大纲）
     */
    AGENT2_COMPLETE("AGENT2_COMPLETE", "大纲生成完成"),

    /**
     * 智能体3流式输出（正文）
     */
    AGENT3_STREAMING("AGENT3_STREAMING", "正文流式输出"),

    /**
     * 智能体3完成（生成正文）
     */
    AGENT3_COMPLETE("AGENT3_COMPLETE", "正文生成完成"),

    /**
     * 智能体4完成（分析配图需求）
     */
    AGENT4_COMPLETE("AGENT4_COMPLETE", "配图需求分析完成"),

    /**
     * 单张配图完成
     */
    IMAGE_COMPLETE("IMAGE_COMPLETE", "单张配图完成"),

    /**
     * 智能体5完成（生成配图）
     */
    AGENT5_COMPLETE("AGENT5_COMPLETE", "配图生成完成"),

    /**
     * 图文合成完成
     */
    MERGE_COMPLETE("MERGE_COMPLETE", "图文合成完成"),

    /**
     * 全部完成
     */
    ALL_COMPLETE("ALL_COMPLETE", "全部完成"),

    /**
     * 内容质量评分完成
     */
    QUALITY_SCORED("QUALITY_SCORED", "内容质量评分完成"),

    /**
     * 质量门检测完成 — 携带表达质量评分/是否触发优化/violations/viralScore
     */
    QUALITY_CHECKED("QUALITY_CHECKED", "质量门检测完成"),

    /**
     * RAG 参考检索完成 — 携带注入的参考数量/阶段
     */
    RAG_REFERENCE_FOUND("RAG_REFERENCE_FOUND", "已注入参考材料"),

    /**
     * 错误
     */
    ERROR("ERROR", "错误");

    /**
     * 消息类型值
     */
    private final String value;

    /**
     * 消息类型描述
     */
    private final String description;

    SseMessageTypeEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * 获取流式输出消息前缀
     * 用于构建带数据的流式消息，如 "AGENT2_STREAMING:内容"
     *
     * @return 消息前缀（带冒号）
     */
    public String getStreamingPrefix() {
        return this.value + ":";
    }
}
