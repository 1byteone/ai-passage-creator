package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.ApprovalRecord;

import java.util.List;

/**
 * 内容审批流 + 发布排期服务
 */
public interface ApprovalService {

    /**
     * 提交文章进入审批（仅文章作者）
     */
    ApprovalRecord submit(String taskId, Long submittedBy);

    /**
     * 审批通过（admin，作者除外）
     */
    ApprovalRecord approve(String taskId, Long reviewerId, String comment);

    /**
     * 审批驳回（admin，作者除外）
     */
    ApprovalRecord reject(String taskId, Long reviewerId, String comment);

    /**
     * 获取文章审批历史（作者或 admin）
     */
    List<ApprovalRecord> getHistory(String taskId, Long userId);

    /**
     * 检查文章当前审批状态（作者或 admin）
     */
    String getStatus(String taskId, Long userId);
}
