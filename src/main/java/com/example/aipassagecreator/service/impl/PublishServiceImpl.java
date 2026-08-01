package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.mapper.PublishScheduleMapper;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.PublishSchedule;
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

    @Resource
    private PublishScheduleMapper scheduleMapper;

    @Resource
    private ArticleMapper articleMapper;

    @Override
    public PublishSchedule schedule(String taskId, LocalDateTime publishAt, Long userId) {
        if (publishAt == null || publishAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "发布时间必须晚于当前时间");
        }
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId));
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }

        PublishSchedule schedule = PublishSchedule.builder()
                .articleTaskId(taskId)
                .publishAt(publishAt)
                .status("SCHEDULED")
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
        schedule.setStatus("CANCELLED");
        scheduleMapper.update(schedule);
        log.info("取消发布排期: scheduleId={}, userId={}", scheduleId, userId);
    }

    @Override
    public List<PublishSchedule> listByArticle(String taskId) {
        return scheduleMapper.selectListByQuery(
                QueryWrapper.create().eq("article_task_id", taskId).orderBy("publish_at", false));
    }

    @Override
    @Scheduled(fixedDelayString = "${publish.check-interval-ms:60000}")
    public int executeDuePublishes() {
        List<PublishSchedule> due = scheduleMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("status", "SCHEDULED")
                        .le("publish_at", LocalDateTime.now()));
        int count = 0;
        for (PublishSchedule schedule : due) {
            try {
                Article article = articleMapper.selectOneByQuery(
                        QueryWrapper.create().eq("taskId", schedule.getArticleTaskId()));
                if (article == null) continue;

                article.setStatus("PUBLISHED");
                articleMapper.update(article);

                schedule.setStatus("PUBLISHED");
                schedule.setPublishedAt(LocalDateTime.now());
                scheduleMapper.update(schedule);
                count++;
                log.info("定时发布完成: scheduleId={}, taskId={}", schedule.getId(), schedule.getArticleTaskId());
            } catch (Exception e) {
                log.error("定时发布失败: scheduleId={}, error={}", schedule.getId(), e.getMessage(), e);
            }
        }
        return count;
    }
}
