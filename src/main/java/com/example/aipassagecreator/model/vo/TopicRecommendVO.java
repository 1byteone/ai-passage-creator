package com.example.aipassagecreator.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 推荐选题 VO — 混合来源（平台热门 + 用户历史 + AI 动态生成）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicRecommendVO {

    /** 推荐选题列表 */
    private List<Item> items;

    /** 是否有 AI 生成的新鲜选题（前端据此显示"热点"徽标开关） */
    private boolean hasAi;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** 选题文本 */
        private String text;
        /** 来源: hot=平台热门 / history=用户历史 / ai=AI 生成 */
        private String source;
    }
}
