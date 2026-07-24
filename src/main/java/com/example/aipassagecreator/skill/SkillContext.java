package com.example.aipassagecreator.skill;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Skill 运行时上下文注册表
 * 替代 StreamHandlerContext 的 ThreadLocal 方案，支持跨线程共享
 */
@Slf4j
@Component
public class SkillContext {

    private static final ConcurrentHashMap<String, RuntimeContext> REGISTRY = new ConcurrentHashMap<>();

    @Data
    public static class RuntimeContext {
        private String executionId;
        private String taskId;
        private SseEmitter emitter;
        private transient Consumer<String> streamHandler;
        private transient Consumer<String> phaseHandler = phase -> {
        };
        private final Map<String, Object> sharedData = new ConcurrentHashMap<>();
        private volatile boolean cancelled = false;
        private volatile String currentPhase;
        private volatile int currentPhaseIndex;
        private int totalPhases;
        private long startTime;
    }

    public static RuntimeContext create(String executionId, SseEmitter emitter) {
        RuntimeContext ctx = new RuntimeContext();
        ctx.setExecutionId(executionId);
        ctx.setEmitter(emitter);
        ctx.setStreamHandler(msg -> {
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event().data(msg).reconnectTime(3000L));
                } catch (IOException e) {
                    log.warn("SSE 推送失败 (executionId={}): {}", executionId, e.getMessage());
                    try { emitter.completeWithError(e); } catch (Exception ignored) {}
                }
            }
        });
        ctx.setStartTime(System.currentTimeMillis());
        REGISTRY.put(executionId, ctx);
        log.debug("SkillContext 已创建: executionId={}", executionId);
        return ctx;
    }

    public static RuntimeContext get(String executionId) {
        return REGISTRY.get(executionId);
    }

    public static void remove(String executionId) {
        REGISTRY.remove(executionId);
        log.debug("SkillContext 已移除: executionId={}", executionId);
    }

    /** 获取当前活跃的上下文数量 */
    public static int activeCount() {
        return REGISTRY.size();
    }
}
