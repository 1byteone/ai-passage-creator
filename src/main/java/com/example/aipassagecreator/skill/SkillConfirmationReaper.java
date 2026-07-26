package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时未确认执行的收割器
 * <p>
 * 用户发起了含确认阶段的 Skill 但迟迟不确认时，配额已扣、检查点常驻内存。
 * 本收割器将超时执行置为 FAILED 并退还配额，避免用户白扣与内存泄漏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillConfirmationReaper {

    private final SkillExecutionRegistry executionRegistry;
    private final SkillExecutionMapper skillExecutionMapper;
    private final SkillSseEmitterManager sseEmitterManager;
    private final SkillExecutionService skillExecutionService;

    @Scheduled(fixedDelayString = "${skill.confirmation.reap-interval-ms:120000}")
    public void reapExpiredConfirmations() {
        List<SkillExecutionRegistry.Expired> expired = executionRegistry.findExpired();
        if (expired.isEmpty()) {
            executionRegistry.purgeTerminal();
            return;
        }

        log.info("发现 {} 个超时未确认的 Skill 执行，开始收割", expired.size());
        for (SkillExecutionRegistry.Expired item : expired) {
            try {
                reapOne(item);
            } catch (Exception e) {
                log.error("收割超时执行失败: executionId={}", item.executionId(), e);
            }
        }
        executionRegistry.purgeTerminal();
    }

    private void reapOne(SkillExecutionRegistry.Expired item) {
        String executionId = item.executionId();

        // 条件更新：仅当记录仍处于 AWAITING_CONFIRMATION 时才收割。
        // 以影响行数判定，避免与用户并发 confirm 造成双重处理、双重退款。
        SkillExecutionPo update = new SkillExecutionPo();
        update.setStatus(SkillExecutionStatusEnum.FAILED.getValue());
        update.setErrorMessage("等待确认超时，已自动取消");

        int affected = skillExecutionMapper.updateByQuery(update, QueryWrapper.create()
                .eq("skill_execution_id", executionId)
                .eq("status", SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue()));

        if (affected == 0) {
            // 用户已在收割前完成确认，交由正常流程处理
            log.debug("收割跳过，状态已变更: executionId={}", executionId);
            executionRegistry.remove(executionId);
            return;
        }

        log.info("Skill 等待确认超时，已取消并退还配额: executionId={}", executionId);
        skillExecutionService.refundQuietly(item.userId(), executionId);

        sseEmitterManager.publish(executionId, SkillEventFactory.error(
                executionId,
                item.execution().getDefinition().getName(),
                item.execution().getDefinition().getPhases().isEmpty()
                        ? null
                        : item.execution().getStatus(),
                "等待确认超时，已自动取消"));
        sseEmitterManager.complete(executionId);

        SkillContext.remove(executionId);
        executionRegistry.remove(executionId);
    }
}
