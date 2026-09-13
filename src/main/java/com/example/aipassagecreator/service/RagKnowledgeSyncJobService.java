package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagSyncJobMapper;
import com.example.aipassagecreator.model.po.RagSyncJob;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 研发知识库同步任务编排：HTTP 请求只负责创建任务，索引在 ragExecutor 中执行。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagKnowledgeSyncJobService {

    private static final String PROJECT_KEY = "ai-passage-creator";

    private final RagSyncJobMapper mapper;
    private final RagKnowledgeSyncService syncService;
    private final RagDocumentStore documentStore;
    private RagKnowledgeSyncJobService selfProxy;

    /** 通过 Spring 代理触发 @Async，避免同类调用绕过异步拦截器。 */
    @Autowired
    public void setSelfProxy(@Lazy RagKnowledgeSyncJobService selfProxy) {
        this.selfProxy = selfProxy;
    }

    public RagSyncJob start(Long createdBy) {
        RagSyncJob running = mapper.selectOneByQuery(QueryWrapper.create()
                .eq(RagSyncJob::getProjectKey, PROJECT_KEY)
                .in(RagSyncJob::getStatus, RagSyncJob.STATUS_QUEUED, RagSyncJob.STATUS_RUNNING)
                .orderBy(RagSyncJob::getCreateTime, false).limit(1));
        if (running != null) return running;

        LocalDateTime now = LocalDateTime.now();
        RagSyncJob job = RagSyncJob.builder()
                .projectKey(PROJECT_KEY)
                .status(RagSyncJob.STATUS_QUEUED)
                .totalFiles(0).processedFiles(0).totalSections(0).indexedSections(0)
                .active(false).retryCount(0).createdBy(createdBy).createTime(now).updateTime(now).build();
        mapper.insert(job);
        (selfProxy == null ? this : selfProxy).executeAsync(job.getId());
        return job;
    }

    @Async("ragExecutor")
    public void executeAsync(Long jobId) {
        RagSyncJob job = mapper.selectOneById(jobId);
        if (job == null) return;
        try {
            markRunning(job);
            RagKnowledgeSyncService.SyncResult result = syncService.syncBatch(String.valueOf(jobId));
            LocalDateTime now = LocalDateTime.now();
            job.setBranchName(result.branch());
            job.setCommitSha(result.commitSha());
            job.setTotalFiles(result.files());
            job.setProcessedFiles(result.files());
            job.setTotalSections(result.sections());
            job.setIndexedSections(result.sections());
            job.setStatus(RagSyncJob.STATUS_SUCCEEDED);
            job.setFinishedAt(now);
            job.setUpdateTime(now);
            mapper.update(job);
            documentStore.activateBatch(String.valueOf(jobId));
            log.info("RAG 知识库同步完成: jobId={}, files={}, sections={}", jobId, result.files(), result.sections());
        } catch (Exception e) {
            job.setStatus(RagSyncJob.STATUS_FAILED);
            job.setErrorMessage(safeMessage(e));
            job.setFinishedAt(LocalDateTime.now());
            job.setUpdateTime(LocalDateTime.now());
            mapper.update(job);
            log.error("RAG 知识库同步失败，保留旧 active 批次: jobId={}", jobId, e);
        }
    }

    public RagSyncJob get(Long jobId) {
        return mapper.selectOneById(jobId);
    }

    /**
     * 从失败任务创建全新的批次重试，绝不复用失败 batchId，避免旧章节残留进入新版本。
     */
    public RagSyncJob retry(Long failedJobId, Long createdBy) {
        RagSyncJob failed = mapper.selectOneById(failedJobId);
        if (failed == null || !PROJECT_KEY.equals(failed.getProjectKey())
                || !RagSyncJob.STATUS_FAILED.equals(failed.getStatus())) {
            return null;
        }
        RagSyncJob running = mapper.selectOneByQuery(QueryWrapper.create()
                .eq(RagSyncJob::getProjectKey, PROJECT_KEY)
                .in(RagSyncJob::getStatus, RagSyncJob.STATUS_QUEUED, RagSyncJob.STATUS_RUNNING)
                .orderBy(RagSyncJob::getCreateTime, false).limit(1));
        if (running != null) return running;

        // 失败批次从未被激活，清理其半成品，避免积累无引用向量。
        documentStore.deleteBatch(String.valueOf(failedJobId));
        LocalDateTime now = LocalDateTime.now();
        RagSyncJob retry = RagSyncJob.builder()
                .projectKey(PROJECT_KEY)
                .status(RagSyncJob.STATUS_QUEUED)
                .totalFiles(0).processedFiles(0).totalSections(0).indexedSections(0)
                .active(false).createdBy(createdBy)
                .retryCount((failed.getRetryCount() == null ? 0 : failed.getRetryCount()) + 1)
                .retryOfJobId(failedJobId).createTime(now).updateTime(now).build();
        mapper.insert(retry);
        (selfProxy == null ? this : selfProxy).executeAsync(retry.getId());
        return retry;
    }

    private void markRunning(RagSyncJob job) {
        job.setStatus(RagSyncJob.STATUS_RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        mapper.update(job);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() > 1900 ? message.substring(0, 1900) : message;
    }
}
