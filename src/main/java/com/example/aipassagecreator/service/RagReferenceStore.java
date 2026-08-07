package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.RagReferenceMapper;
import com.example.aipassagecreator.model.po.RagReference;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 参考溯源存储 — 把创作各阶段注入的检索参考持久化，供详情页溯源。
 * <p>失败静默（log.warn），不影响生成主流程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagReferenceStore {

    private final RagReferenceMapper mapper;

    /** 保存某阶段注入的参考（先清该 taskId+stage 旧记录再插入，幂等） */
    public void saveStage(String taskId, String stage, List<RagAugmentationService.Reference> references) {
        if (taskId == null || taskId.isBlank() || references == null || references.isEmpty()) {
            return;
        }
        try {
            mapper.deleteByQuery(QueryWrapper.create()
                    .eq(RagReference::getTaskId, taskId)
                    .eq(RagReference::getStage, stage));
            List<RagReference> rows = references.stream()
                    .map(r -> RagReference.builder()
                            .taskId(taskId)
                            .stage(stage)
                            .refId(r.refId())
                            .refType(r.type())
                            .refTitle(r.title())
                            .score(r.score())
                            .build())
                    .toList();
            mapper.insertBatch(rows);
            log.info("RAG 参考溯源已保存: taskId={}, stage={}, count={}", taskId, stage, rows.size());
        } catch (Exception e) {
            log.warn("RAG 参考溯源保存失败（不影响主流程）: taskId={}, stage={}, err={}", taskId, stage, e.getMessage());
        }
    }

    /** 查询某文章的全部参考溯源（按阶段升序、相关度降序） */
    public List<RagReference> findByTaskId(String taskId) {
        return mapper.selectListByQuery(QueryWrapper.create()
                .eq(RagReference::getTaskId, taskId)
                .orderBy(RagReference::getStage, true)
                .orderBy(RagReference::getScore, false));
    }
}
