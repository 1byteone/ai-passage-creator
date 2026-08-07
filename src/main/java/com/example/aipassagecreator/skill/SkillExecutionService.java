package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.QuotaService;
import com.example.aipassagecreator.service.RagService;
import com.example.aipassagecreator.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Skill 异步执行服务
 * 作为 Spring Bean，确保 @Async 注解生效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillExecutionService {

    private final SkillSseEmitterManager sseEmitterManager;
    private final SkillExecutionRegistry executionRegistry;
    private final QuotaService quotaService;
    private final UserService userService;
    private final SkillRegistry skillRegistry;
    private final RagService ragService;

    /**
     * 公共派发：配额校验与扣减 → 创建执行 → prepare → 含确认则注册 → executeAsync。
     * <p>
     * 由 {@link SkillController#executeSkill} 和 {@code ArticleController#executeArticleSkill}
     * 共用，消除 Copy-Paste Sprawl。角色校验仍由各 Controller 负责（Skill 有 requiredRoles、
     * 文章一键执行仅校验归属 + COMPLETED 状态）。
     *
     * @param skillName Skill 名称
     * @param inputs    初始输入
     * @param user      执行用户（用于配额校验与 userId 注入）
     * @return 已 create+prepare 的 SkillExecution（executeAsync 已触发）
     */
    public SkillExecution dispatchAndExecute(String skillName, Map<String, Object> inputs, User user) {
        quotaService.checkAndConsumeQuota(user, "配额不足，无法执行此 Skill");

        SkillExecution execution;
        try {
            execution = skillRegistry.createExecution(skillName, inputs);
            execution.prepare(user.getId());
            if (skillRegistry.hasConfirmationPhase(skillName)) {
                executionRegistry.register(execution, user.getId());
            }
            executeAsync(execution, user.getId());
        } catch (Exception e) {
            log.error("Skill 派发失败，退还配额: skillName={}, userId={}", skillName, user.getId(), e);
            quotaService.refundQuota(user);
            throw e;
        }
        return execution;
    }

    @Async("skillExecutor")
    public void executeAsync(SkillExecution execution, Long userId) {
        execution.execute(
                event -> sseEmitterManager.publish(execution.getExecutionId(), event),
                userId
        );
        settle(execution, userId);
    }

    /**
     * 用户确认后续跑
     *
     * @param modifiedData modify 动作携带的修改数据，approve 时为 null
     * @param retry        是否重新生成当前待确认阶段（清除该阶段输出重跑）
     */
    @Async("skillExecutor")
    public void resumeAsync(SkillExecution execution, Long userId, Map<String, Object> modifiedData,
                            boolean retry) {
        execution.resume(
                modifiedData,
                event -> sseEmitterManager.publish(execution.getExecutionId(), event),
                retry
        );
        settle(execution, userId);
    }

    /**
     * 异步执行整条链 — HTTP 请求立即返回 chainId，进度经 SSE 推送。
     * <p>
     * 链上某个 skill 失败已在 executeSync 内退还失败及后续 skill 的配额；
     * 此处仅在整条链抛出异常（派发/图编译等灾难性错误）时退还全部配额。
     */
    @Async("skillExecutor")
    public void executeChainAsync(SkillExecutionChain chain, Map<String, Object> inputs,
                                  Long userId, int quotaCount) {
        String chainId = chain.getChainExecutionId();
        try {
            chain.executeSync(
                    event -> sseEmitterManager.publish(chainId, event),
                    inputs, userId);
        } catch (Exception e) {
            log.error("链式编排异常终止，退还配额: chainId={}", chainId, e);
            for (int i = 0; i < quotaCount; i++) {
                refundQuietly(userId, chainId + "-" + i);
            }
        } finally {
            sseEmitterManager.complete(chainId);
            executionRegistry.unregisterChain(chainId);
        }
    }

    /**
     * 一次执行片段结束后的收尾
     * <p>
     * 暂停等待确认时 <b>不能</b> 关闭 SSE —— 前端需保持连接以接收续跑后的事件。
     * 仅终态才关闭连接并从注册表移除。
     */
    private void settle(SkillExecution execution, Long userId) {
        if (execution.isAwaitingConfirmation()) {
            log.info("Skill 等待用户确认，保持 SSE 连接: executionId={}", execution.getExecutionId());
            // 多确认点 Skill 二次暂停后刷新注册时间，避免 TTL 提前耗尽被收割
            executionRegistry.register(execution, userId);
            return;
        }
        try {
            // 执行失败用户未获得任何产出，退还配额
            if (SkillExecutionStatusEnum.FAILED.getValue().equals(execution.getStatus())) {
                refundQuietly(userId, execution.getExecutionId());
            }
            // P1：Skill 成功产出入向量库（失败静默，indexSkillAsync 内置 try-catch）
            if (SkillExecutionStatusEnum.SUCCESS.getValue().equals(execution.getStatus())) {
                SkillExecutionPo po = execution.getPoForIndex();
                if (po != null) {
                    ragService.indexSkillAsync(po, execution.getPersistedOutput());
                }
            }
        } finally {
            executionRegistry.remove(execution.getExecutionId());
            sseEmitterManager.complete(execution.getExecutionId());
        }
    }

    /**
     * 退还配额，失败不影响 SSE 收尾
     */
    void refundQuietly(Long userId, String executionId) {
        try {
            User user = userService.getById(userId);
            if (user == null) {
                log.warn("Skill 执行失败退款跳过，用户不存在: userId={}, executionId={}", userId, executionId);
                return;
            }
            quotaService.refundQuota(user);
            log.info("Skill 执行失败，已退还配额: userId={}, executionId={}", userId, executionId);
        } catch (Exception e) {
            log.error("Skill 执行失败退款异常: userId={}, executionId={}", userId, executionId, e);
        }
    }
}
