package com.example.aipassagecreator.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 增强版数据分析 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsVO {

    /** 文章总量/风格分布 */
    private Long totalArticles;
    private Map<String, Long> styleDistribution;

    /** 配图方式分布 */
    private Map<String, Long> imageMethodDistribution;

    /** 质量评分趋势（近 N 次平均分） */
    private List<Double> qualityTrend;
    private Double avgQualityScore;

    /** 技能使用排行 */
    private Map<String, Long> skillUsageTop;
    private Map<String, Long> modelUsage;

    /** 用户活跃度 */
    private Map<String, Long> dailyActiveUsers;

    /** 配额消耗 */
    private Long quotaConsumed;

    /** 成功率 */
    private Double successRate;

    /** Token 总消耗 */
    private Long totalTokenUsage;
}
