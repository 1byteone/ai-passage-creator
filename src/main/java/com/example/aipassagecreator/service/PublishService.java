package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.PublishSchedule;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章发布排期服务
 */
public interface PublishService {

    /**
     * 创建发布排期（仅文章作者，需已审批通过）
     * @param platform 目标平台 (wechat/xiaohongshu/douyin)
     * @param methodologyName 方法论模板 (默认使用 platform 同名)
     */
    PublishSchedule schedule(String taskId, LocalDateTime publishAt, String platform,
                             String methodologyName, Long userId);

    /**
     * 取消排期（仅创建者或 admin）
     */
    void cancel(Long scheduleId, Long userId);

    /**
     * 查询文章排期（作者或 admin）
     */
    List<PublishSchedule> listByArticle(String taskId, Long userId);

    /**
     * 执行到期的发布任务（@Scheduled 调用）。
     * 对每条到期排期：读取文章 → 平台适配转换 → 校验 → 存储 adapterOutput → 更新状态。
     */
    int executeDuePublishes();
}
