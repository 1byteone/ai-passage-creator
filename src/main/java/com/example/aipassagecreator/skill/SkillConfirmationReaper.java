package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时未确认执行的收割器（DB 驱动）
 * <p>
 * 用户发起了含确认阶段的 Skill 但迟迟不确认时，配额已扣、检查点常驻 DB。
 * 本收割器直接扫 {@code skill_execution} 表中超时的 AWAITING_CONFIRMATION 行，
 * 条件更新抢占为 FAILED 后退还配额 —— 应用重启后 DB 中的孤儿行同样能被收割，
 * 不再依赖进程内注册表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillConfirmationReaper {

    /** 等待确认的最长时长，超时即收割（与 SkillExecutionRegistry 定义一致） */
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(10);

    private final SkillExecutionRegistry executionRegistry;
    private final SkillExecutionMapper skillExecutionMapper;
    private final SkillSseEmitterManager sseEmitterManager;
    private final SkillExecutionService skillExecutionService;

    @Scheduled(fixedDelayString = "${skill.confirmation.reap-interval-ms:120000}")
    public void reapExpiredConfirmations() {
        LocalDateTime deadline = LocalDateTime.now().minus(CONFIRMATION_TTL);
        List<SkillExecutionPo> stale = skillExecutionMapper.selectListByQuery(QueryWrapper.create()
                .eq("status", SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue())
                .lt("update_time", deadline));
        if (stale.isEmpty()) {
            return;
        }

        log.info("发现 {} 个超时未确认的 Skill 执行，开始收割", stale.size());
        for (SkillExecutionPo po : stale) {
            try {
                reapOne(po);
            } catch (Exception e) {
                log.error("收割超时执行失败: executionId={}", po.getSkillExecutionId(), e);
            }
        }
    }

    private void reapOne(SkillExecutionPo po) {
        String executionId = po.getSkillExecutionId();

        // 条件更新抢占：仅当记录仍处于 AWAITING_CONFIRMATION 时才收割。
        // 以影响行数判定，避免与用户并发 confirm 造成双重处理、双重退款。
        SkillExecutionPo update = new SkillExecutionPo();
        update.setStatus(SkillExecutionStatusEnum.FAILED.getValue());
        update.setErrorMessage("等待确认超时，已自动取消");
        update.setUpdateTime(LocalDateTime.now());

        int affected = skillExecutionMapper.updateByQuery(update, QueryWrapper.create()
                .eq("skill_execution_id", executionId)
                .eq("status", SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue()));
        if (affected == 0) {
            // 用户已在收割前完成确认（confirm 已把状态抢占为 RUNNING），交由正常流程处理。
            log.debug("收割跳过，状态已变更: executionId={}", executionId);
            return;
        }

        log.info("Skill 等待确认超时，已取消并退还配额: executionId={}", executionId);
        skillExecutionService.refundQuietly(po.getUserId(), executionId);

        // 若内存中仍有实例（同一进程内超时），同步清理其上下文并推送超时事件；
        // 重启后仅剩 DB 行时，SSE 通道不存在，complete 为幂等空操作。
        SkillExecution inMemory = executionRegistry.get(executionId);
        if (inMemory != null) {
            sseEmitterManager.publish(executionId, SkillEventFactory.error(
                    executionId,
                    inMemory.getDefinition().getName(),
                    inMemory.getPendingPhase(),
                    "等待确认超时，已自动取消"));
        }
        sseEmitterManager.complete(executionId);

        SkillContext.remove(executionId);
        executionRegistry.remove(executionId);
    }
}
