package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.PublishSchedule;

import java.util.List;

/**
 * 文章发布排期服务
 */
public interface PublishService {

    /**
     * 创建发布排期
     */
    PublishSchedule schedule(String taskId, java.time.LocalDateTime publishAt, Long userId);

    /**
     * 取消排期
     */
    void cancel(Long scheduleId, Long userId);

    /**
     * 查询文章排期
     */
    List<PublishSchedule> listByArticle(String taskId);

    /**
     * 执行到期的发布任务（@Scheduled 调用）
     */
    int executeDuePublishes();
}
