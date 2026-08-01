package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.ArticleVersion;

import java.util.List;

/**
 * 文章多轮改写 + 版本历史服务
 */
public interface ArticleRewriteService {

    /**
     * 执行一轮 AI 改写
     *
     * @param taskId      文章任务 ID
     * @param instruction 改写指令（可选，为空则自动生成优化方向）
     * @param maxRounds   最大改写轮次
     * @param userId      用户 ID
     * @return 最新版本
     */
    ArticleVersion rewrite(String taskId, String instruction, int maxRounds, Long userId);

    /**
     * 按章节定向改写（v1 退化为整篇改写，sectionLocator 预留）
     *
     * @param taskId         文章任务 ID
     * @param instruction    定向改写指令
     * @param sectionLocator 章节定位符（v1 预留，传 null 表示整篇）
     * @param userId         用户 ID
     * @return 新建的版本
     */
    ArticleVersion rewriteSection(String taskId, String instruction, String sectionLocator, Long userId);

    /**
     * 获取文章所有版本历史
     */
    List<ArticleVersion> getVersionHistory(String taskId);

    /**
     * 回退到指定版本
     */
    ArticleVersion revertTo(String taskId, int versionNo, Long userId);
}
