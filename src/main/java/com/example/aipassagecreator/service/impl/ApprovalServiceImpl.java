package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.ApprovalRecordMapper;
import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.mapper.UserMapper;
import com.example.aipassagecreator.model.po.ApprovalRecord;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.ApprovalService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ApprovalServiceImpl implements ApprovalService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Resource
    private ApprovalRecordMapper approvalMapper;

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    public ApprovalRecord submit(String taskId, Long submittedBy) {
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId));
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }
        // 归属校验：仅文章作者可提交审批，防止 IDOR
        if (!article.getUserId().equals(submittedBy)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权提交此文章审批");
        }
        if (!ArticleStatusEnum.COMPLETED.getValue().equals(article.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "仅已完成文章可提交审批，当前状态: " + article.getStatus());
        }

        ApprovalRecord record = ApprovalRecord.builder()
                .articleTaskId(taskId)
                .status(STATUS_PENDING)
                .submittedBy(submittedBy)
                .submitTime(LocalDateTime.now())
                .build();
        approvalMapper.insert(record);
        log.info("提交审批: taskId={}, approvalId={}", taskId, record.getId());
        return record;
    }

    @Override
    public ApprovalRecord approve(String taskId, Long reviewerId, String comment) {
        return review(taskId, reviewerId, comment, STATUS_APPROVED);
    }

    @Override
    public ApprovalRecord reject(String taskId, Long reviewerId, String comment) {
        return review(taskId, reviewerId, comment, STATUS_REJECTED);
    }

    private ApprovalRecord review(String taskId, Long reviewerId, String comment, String status) {
        // 校验文章存在
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId));
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }
        // 作者不能审批自己的文章（避免自审自批）
        if (article.getUserId().equals(reviewerId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "作者不能审批自己的文章");
        }

        ApprovalRecord pending = approvalMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("article_task_id", taskId)
                        .eq("status", STATUS_PENDING)
                        .orderBy("id", false)
                        .limit(1));
        if (pending == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "没有待审批的记录");
        }
        pending.setStatus(status);
        pending.setReviewerId(reviewerId);
        pending.setComment(comment);
        pending.setReviewTime(LocalDateTime.now());
        approvalMapper.update(pending);

        // 同步文章状态（状态机：REJECTED 与 APPROVED 互斥）
        if (article != null) {
            article.setStatus(STATUS_APPROVED.equals(status) ? "APPROVED" : "REJECTED");
            articleMapper.update(article);
        }

        log.info("审批 {}: taskId={}, reviewer={}, status={}", status, taskId, reviewerId, status);
        return pending;
    }

    @Override
    public List<ApprovalRecord> getHistory(String taskId, Long userId) {
        requireOwnerOrAdmin(taskId, userId);
        return approvalMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("article_task_id", taskId)
                        .orderBy("id", true));
    }

    @Override
    public String getStatus(String taskId, Long userId) {
        requireOwnerOrAdmin(taskId, userId);
        ApprovalRecord latest = approvalMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("article_task_id", taskId)
                        .orderBy("id", false)
                        .limit(1));
        return latest == null ? null : latest.getStatus();
    }

    /**
     * 归属校验：文章作者或管理员才可查看审批记录，防止 IDOR
     */
    private void requireOwnerOrAdmin(String taskId, Long userId) {
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId));
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }
        boolean isOwner = article.getUserId().equals(userId);
        boolean isAdmin = isAdminUser(userId);
        if (!isOwner && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看此文章审批记录");
        }
    }

    private boolean isAdminUser(Long userId) {
        try {
            User user = userMapper.selectOneById(userId);
            return user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole());
        } catch (Exception e) {
            return false;
        }
    }
}
