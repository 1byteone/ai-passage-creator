package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.vo.AnalyticsVO;

/**
 * 增强版数据分析服务
 */
public interface AnalyticsService {

    /**
     * 获取全站内容分析指标（admin 专属）
     */
    AnalyticsVO getContentAnalytics();

    /**
     * 获取指定用户的创作分析
     */
    AnalyticsVO getUserAnalytics(Long userId);
}
