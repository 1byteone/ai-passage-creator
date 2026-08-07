package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.utils.GsonUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agent 对话流事件工厂 — 统一 SSE 事件协议（与 SkillEventFactory 同风格） */
public final class AgentEventFactory {

    private AgentEventFactory() {
    }

    /** 参考来源（RAG 命中） */
    public record SourceRef(String refId, String title, String type, String createdAt) {
    }

    public static String started(String requestId) {
        return GsonUtils.toJson(base("agent.chat_started", requestId));
    }

    public static String textDelta(String requestId, String text) {
        Map<String, Object> payload = base("agent.text_delta", requestId);
        payload.put("text", text);
        return GsonUtils.toJson(payload);
    }

    public static String ragReference(String requestId, List<SourceRef> sources) {
        Map<String, Object> payload = base("agent.rag_reference", requestId);
        payload.put("sources", sources);
        return GsonUtils.toJson(payload);
    }

    public static String skillStarted(String requestId, String skillName, int totalPhases) {
        Map<String, Object> payload = base("agent.skill_started", requestId);
        payload.put("skillName", skillName);
        payload.put("totalPhases", totalPhases);
        return GsonUtils.toJson(payload);
    }

    public static String skillPhase(String requestId, int phase, int total, String name, int progress) {
        Map<String, Object> payload = base("agent.skill_phase", requestId);
        payload.put("phase", phase);
        payload.put("total", total);
        payload.put("name", name);
        payload.put("progress", progress);
        return GsonUtils.toJson(payload);
    }

    public static String skillConfirm(String requestId, String executionId, String phase) {
        Map<String, Object> payload = base("agent.skill_confirm", requestId);
        payload.put("executionId", executionId);
        payload.put("phase", phase);
        payload.put("supportedActions", List.of("approve", "modify", "retry"));
        return GsonUtils.toJson(payload);
    }

    public static String complete(String requestId, Long messageId) {
        Map<String, Object> payload = base("agent.complete", requestId);
        payload.put("messageId", messageId);
        return GsonUtils.toJson(payload);
    }

    public static String error(String requestId, String message) {
        Map<String, Object> payload = base("agent.error", requestId);
        payload.put("message", message);
        return GsonUtils.toJson(payload);
    }

    private static Map<String, Object> base(String type, String requestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("agentRequestId", requestId);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }
}