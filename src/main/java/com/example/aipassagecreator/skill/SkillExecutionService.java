package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.QuotaService;
import com.example.aipassagecreator.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Skill 异步执行服务
 * 作为 Spring Bean，确保 @Async 注解生效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillExecutionService {

    private static final String STATUS_FAILED = "FAILED";

    private final SkillSseEmitterManager sseEmitterManager;
    private final QuotaService quotaService;
    private final UserService userService;

    @Async("skillExecutor")
    public void executeAsync(SkillExecution execution, Long userId) {
        try {
            execution.execute(
                    event -> sseEmitterManager.publish(execution.getExecutionId(), event),
                    userId
            );
            // 执行失败用户未获得任何产出，退还配额
            // execute() 内部已捕获全部异常且不外抛，故此处以最终状态判定
            if (STATUS_FAILED.equals(execution.getStatus())) {
                refundQuietly(userId, execution.getExecutionId());
            }
        } finally {
            sseEmitterManager.complete(execution.getExecutionId());
        }
    }

    /**
     * 退还配额，失败不影响 SSE 收尾
     */
    private void refundQuietly(Long userId, String executionId) {
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
