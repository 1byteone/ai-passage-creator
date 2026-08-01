package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.ApprovalRecord;

import java.util.List;

/**
 * 内容审批流 + 发布排期服务
 */
public interface ApprovalService {

    /**
     * 提交文章进入审批
     */
    ApprovalRecord submit(String taskId, Long submittedBy);

    /**
     * 审批通过
     */
    ApprovalRecord approve(String taskId, Long reviewerId, String comment);

    /**
     * 审批驳回
     */
    ApprovalRecord reject(String taskId, Long reviewerId, String comment);

    /**
     * 获取文章审批历史
     */
    List<ApprovalRecord> getHistory(String taskId);

    /**
     * 检查文章当前审批状态
     */
    String getStatus(String taskId);
}
