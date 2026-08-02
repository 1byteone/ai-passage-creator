package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.mapper.PublishScheduleMapper;
import com.example.aipassagecreator.mapper.UserMapper;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.PublishSchedule;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.publish.platform.ContentPublisher;
import com.example.aipassagecreator.service.PublishService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class PublishServiceImpl implements PublishService {

    private static final String STATUS_SCHEDULED = "SCHEDULED";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    @Resource
    private PublishScheduleMapper scheduleMapper;

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ApprovalServiceImpl approvalService;

    @Resource
    private ContentPublisher contentPublisher;

    @Override
    public PublishSchedule schedule(String taskId, LocalDateTime publishAt, String platform,
                                    String methodologyName, Long userId) {
        if (publishAt == null || publishAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "发布时间必须晚于当前时间");
        }
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId));
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }
        // 归属校验：仅文章作者可排期发布
        if (!article.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权为他人文章创建排期");
        }
        // 审批门槛：需已审批通过
        String approvalStatus = approvalService.getStatus(taskId, userId);
        if (!ApprovalServiceImpl.STATUS_APPROVED.equals(approvalStatus)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "仅已审批通过的文章可排期发布，当前审批状态: " + approvalStatus);
        }

        // 方法论追溯：请求值 → 文章创建时方法论 → 平台对应方法论 → 默认 "default"
        String effectiveMethodology = methodologyName != null && !methodologyName.isBlank()
                ? methodologyName
                : (article.getMethodology() != null && !article.getMethodology().isBlank()
                        ? article.getMethodology() : platform);

        PublishSchedule schedule = PublishSchedule.builder()
                .articleTaskId(taskId)
                .publishAt(publishAt)
                .platform(platform != null ? platform : "wechat")
                .methodologyName(effectiveMethodology)
                .status(STATUS_SCHEDULED)
                .createdBy(userId)
                .build();
        scheduleMapper.insert(schedule);
        log.info("创建发布排期: taskId={}, publishAt={}", taskId, publishAt);
        return schedule;
    }

    @Override
    public void cancel(Long scheduleId, Long userId) {
        PublishSchedule schedule = scheduleMapper.selectOneById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "排期不存在");
        }
        // 归属校验：仅创建者或 admin 可取消
        if (!schedule.getCreatedBy().equals(userId) && !isAdminUser(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权取消此排期");
        }
        schedule.setStatus(STATUS_CANCELLED);
        scheduleMapper.update(schedule);
        log.info("取消发布排期: scheduleId={}, userId={}", scheduleId, userId);
    }

    @Override
    public List<PublishSchedule> listByArticle(String taskId, Long userId) {
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId));
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }
        // 归属校验：作者或 admin 可查看排期
        if (!article.getUserId().equals(userId) && !isAdminUser(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看此文章排期");
        }
        return scheduleMapper.selectListByQuery(
                QueryWrapper.create().eq("article_task_id", taskId).orderBy("publish_at", false));
    }

    @Override
    @Scheduled(fixedDelayString = "${publish.check-interval-ms:60000}")
    public int executeDuePublishes() {
        List<PublishSchedule> due = scheduleMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("status", STATUS_SCHEDULED)
                        .le("publish_at", LocalDateTime.now())
                        .limit(10));
        int count = 0;
        for (PublishSchedule schedule : due) {
            try {
                Article article = articleMapper.selectOneByQuery(
                        QueryWrapper.create().eq("taskId", schedule.getArticleTaskId()));
                if (article == null) continue;

                // 发布前复核审批通过状态
                String approvalStatus = approvalService.getStatus(
                        schedule.getArticleTaskId(), article.getUserId());
                if (!ApprovalServiceImpl.STATUS_APPROVED.equals(approvalStatus)) {
                    log.warn("发布跳过：文章未通过审批, taskId={}", schedule.getArticleTaskId());
                    continue;
                }

                // 平台适配转换
                String platform = schedule.getPlatform() != null ? schedule.getPlatform() : "wechat";
                String methodology = schedule.getMethodologyName() != null
                        ? schedule.getMethodologyName() : platform;
                String adapterOutput = contentPublisher.convertAndValidate(
                        article, platform, methodology);

                schedule.setContentTitle(article.getMainTitle());
                schedule.setAdapterOutput(adapterOutput);
                schedule.setStatus(STATUS_PUBLISHED);
                schedule.setPublishedAt(LocalDateTime.now());
                scheduleMapper.update(schedule);
                count++;
                log.info("定时发布完成: scheduleId={}, taskId={}, platform={}",
                        schedule.getId(), schedule.getArticleTaskId(), platform);
            } catch (IllegalArgumentException e) {
                log.error("平台适配器异常: scheduleId={}, error={}", schedule.getId(), e.getMessage());
                schedule.setStatus("FAILED");
                scheduleMapper.update(schedule);
            } catch (Exception e) {
                log.error("定时发布失败: scheduleId={}, error={}", schedule.getId(), e.getMessage(), e);
                schedule.setStatus("FAILED");
                scheduleMapper.update(schedule);
            }
        }
        return count;
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
