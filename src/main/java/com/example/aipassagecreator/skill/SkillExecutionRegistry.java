package com.example.aipassagecreator.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存活中的 Skill 执行实例注册表
 * <p>
 * 仅为「多轮确认」而存在：执行在中断点暂停后，其 {@link SkillExecution}
 * （持有 CompiledGraph 与检查点上下文）必须留存，confirm 时才能取回并续跑。
 * <p>
 * 检查点使用 MemorySaver（进程内），因此本注册表同为进程内。
 * 应用重启后待确认的执行无法续跑，confirm 会返回明确的过期错误。
 */
@Slf4j
@Component
public class SkillExecutionRegistry {

    /** 等待确认的最长时长，超时由 SkillConfirmationReaper 收割 */
    public static final Duration CONFIRMATION_TTL = Duration.ofMinutes(10);

    /** 链式执行是短命一次性管道（无 DB 行），TTL 后懒清理防泄漏 */
    private static final Duration CHAIN_TTL = Duration.ofMinutes(30);

    private final Map<String, Entry> registry = new ConcurrentHashMap<>();

    /** chainId → 归属用户 + 登记时间（progress 端点校验所有权） */
    private final Map<String, ChainOwner> chainOwners = new ConcurrentHashMap<>();

    private record Entry(SkillExecution execution, Long userId, Instant registeredAt) {
    }

    private record ChainOwner(Long userId, Instant registeredAt) {
    }

    /**
     * 登记执行实例。仅含确认阶段的 Skill 需要登记。
     */
    public void register(SkillExecution execution, Long userId) {
        registry.put(execution.getExecutionId(), new Entry(execution, userId, Instant.now()));
        log.debug("Skill 执行已登记: executionId={}, 当前存活={}",
                execution.getExecutionId(), registry.size());
    }

    public SkillExecution get(String executionId) {
        Entry entry = registry.get(executionId);
        return entry == null ? null : entry.execution();
    }

    public void remove(String executionId) {
        if (registry.remove(executionId) != null) {
            log.debug("Skill 执行已移出注册表: executionId={}", executionId);
        }
    }

    /**
     * 找出等待确认已超时的执行
     */
    public List<Expired> findExpired() {
        Instant deadline = Instant.now().minus(CONFIRMATION_TTL);
        List<Expired> expired = new ArrayList<>();
        registry.forEach((executionId, entry) -> {
            if (entry.registeredAt().isBefore(deadline) && entry.execution().isAwaitingConfirmation()) {
                expired.add(new Expired(executionId, entry.execution(), entry.userId()));
            }
        });
        return expired;
    }

    /**
     * 清理已到终态的执行，避免注册表无限增长
     */
    public void purgeTerminal() {
        registry.entrySet().removeIf(e -> e.getValue().execution().isTerminal());
    }

    public int activeCount() {
        return registry.size();
    }

    public record Expired(String executionId, SkillExecution execution, Long userId) {
    }

    // ---------- 链式执行归属 ----------

    public void registerChain(String chainId, Long userId) {
        purgeExpiredChains();
        chainOwners.put(chainId, new ChainOwner(userId, Instant.now()));
    }

    public boolean isChainOwner(String chainId, Long userId) {
        ChainOwner owner = chainOwners.get(chainId);
        if (owner == null) {
            return false;
        }
        if (owner.registeredAt().isBefore(Instant.now().minus(CHAIN_TTL))) {
            chainOwners.remove(chainId, owner);
            return false;
        }
        return owner.userId().equals(userId);
    }

    public void unregisterChain(String chainId) {
        chainOwners.remove(chainId);
    }

    private void purgeExpiredChains() {
        Instant deadline = Instant.now().minus(CHAIN_TTL);
        chainOwners.entrySet().removeIf(e -> e.getValue().registeredAt().isBefore(deadline));
    }
}
