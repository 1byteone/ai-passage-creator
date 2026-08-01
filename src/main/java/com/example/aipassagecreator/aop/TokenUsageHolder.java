package com.example.aipassagecreator.aop;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;

/**
 * Token 用量线程持有者
 * <p>
 * 桥接 ChatModel 调用层与 AOP 日志层：Agent 方法内调用 LLM 后通过
 * {@link #record(ChatResponse, String)} 将用量写入此处，
 * AgentExecutionAspect 在方法收尾时读取并持久化到 AgentLog。
 * 每次读取后自动清除，避免泄漏到下一次调用。
 */
public final class TokenUsageHolder {

    private static final ThreadLocal<Integer> TOKEN_USAGE = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<String> MODEL_USED = ThreadLocal.withInitial(() -> null);

    private TokenUsageHolder() {
    }

    /**
     * 从 ChatResponse 中提取 Token 用量与模型名，写入线程持有者
     * <p>
     * 各 Agent 在 LLM 调用完成后调用此方法。
     * 流式调用时 response 可能为 null（末尾分片无 choices），此时不做覆盖。
     *
     * @param response  非流式调用的响应，或流式调用的最后一片
     * @param modelName 实际使用的模型名称
     */
    public static void record(ChatResponse response, String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            MODEL_USED.set(modelName);
        }
        if (response != null) {
            int tokens = extractTotalTokens(response);
            if (tokens > 0) {
                TOKEN_USAGE.set(tokens);
            }
        }
    }

    /**
     * 显式记录 Token 用量（用于流式场景，取各分片最大值）
     */
    public static void recordTokens(int tokens) {
        if (tokens > 0) {
            TOKEN_USAGE.set(tokens);
        }
    }

    /**
     * 显式记录模型名
     */
    public static void recordModel(String model) {
        if (model != null && !model.isBlank()) {
            MODEL_USED.set(model);
        }
    }

    public static int getAndClearTokenUsage() {
        int tokens = TOKEN_USAGE.get();
        TOKEN_USAGE.remove();
        return tokens;
    }

    public static String getAndClearModelUsed() {
        String model = MODEL_USED.get();
        MODEL_USED.remove();
        return model;
    }

    public static void clear() {
        TOKEN_USAGE.remove();
        MODEL_USED.remove();
    }

    private static int extractTotalTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return 0;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null) {
            return 0;
        }
        return usage.getTotalTokens();
    }
}