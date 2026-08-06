package com.example.aipassagecreator.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 方法论模板 VO — 模板市场展示用（精简字段，避免暴露完整 Definition 内部结构）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodologyVO {

    /** 模板名（default/douyin/xiaohongshu/wechat） */
    private String name;

    /** 模板描述 */
    private String description;

    /** 适用平台名 */
    private String platformName;

    /** 目标人群 */
    private String audience;

    /** 字数下限 */
    private Integer minChars;

    /** 字数上限 */
    private Integer maxChars;

    /** 卡片风格（warm/minimal/free） */
    private String cardStyle;

    /** 核心创作维度名列表 */
    private List<String> dimensionNames;
}
