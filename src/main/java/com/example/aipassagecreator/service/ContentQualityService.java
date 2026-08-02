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

    /**
     * 爆款维度评测：按方法论维度加权归一化出爆款综合分，幂等落库（同 taskId 复用行）
     *
     * @param taskId          文章任务 ID
     * @param methodologyName 方法论模板名称（默认 default）
     * @param loginUserId     当前登录用户 ID
     * @return 爆款评测结果
     * @throws IllegalArgumentException 文章不存在或未完成、方法论不存在时抛出
     */
    ArticleQuality evaluateViral(String taskId, String methodologyName, Long loginUserId);

    /**
     * 获取文章最新爆款评测（score_type='VIRAL'）
     *
     * @param taskId 文章任务 ID
     * @return 最新爆款评测，无记录时返回 null
     */
    ArticleQuality getLatestViral(String taskId);

    /**
     * 反哺回退后恢复评分行：删除当前 VIRAL 行并重新落库给定评测。
     * <p>evaluateViral 是 delete+insert 幂等（每任务仅一行），反哺无提升回退后，
     * 当前 VIRAL 行描述的是已回退掉的内容。恢复前轮评测使 getLatestViral 与回退后内容一致。</p>
     *
     * @param taskId  文章任务 ID
     * @param quality 要恢复的历史评测（回退目标内容对应的评分），null 时仅删除当前行
     * @return 恢复后的评测
     */
    ArticleQuality restoreViral(String taskId, ArticleQuality quality);
}
