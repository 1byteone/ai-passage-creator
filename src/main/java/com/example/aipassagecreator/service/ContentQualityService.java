package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.ArticleQuality;

/**
 * 内容质量评分服务
 */
public interface ContentQualityService {

    /**
     * 对指定文章进行质量评分
     *
     * @param taskId 文章任务 ID
     * @return 评分结果
     */
    ArticleQuality evaluate(String taskId);

    /**
     * 获取文章最新评分
     *
     * @param taskId 文章任务 ID
     * @return 最新评分，无记录时返回 null
     */
    ArticleQuality getLatest(String taskId);
}
