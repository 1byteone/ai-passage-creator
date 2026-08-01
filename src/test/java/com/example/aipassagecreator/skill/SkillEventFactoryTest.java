package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillEventFactory 单元测试
 * <p>
 * 覆盖所有 6 种 SSE 事件类型的 JSON 格式和必需字段。
 */
class SkillEventFactoryTest {

    private static final String EXECUTION_ID = "execution-1";
    private static final String SKILL_NAME = "proofreading";
    private static final String PHASE = "ai_tone_fix";
    private static final int PHASE_INDEX = 2;
    private static final int TOTAL_PHASES = 3;

    // ──────────────────────────── started ────────────────────────────

    @Test
    void startedEventHasAllFields() {
        String json = SkillEventFactory.started(EXECUTION_ID, SKILL_NAME, TOTAL_PHASES);
        Map<String, Object> payload = parse(json);

        assertEquals("skill.started", payload.get("type"));
        assertEquals(EXECUTION_ID, payload.get("skillExecutionId"));
        assertEquals(SKILL_NAME, payload.get("skillName"));
        assertEquals(TOTAL_PHASES, ((Number) payload.get("totalPhases")).intValue());
        assertNotNull(payload.get("timestamp"));
    }

    // ──────────────────────────── phaseStarted ────────────────────────────

    @Test
    void phaseStartedEventIncludesPhaseInfo() {
        String json = SkillEventFactory.phaseStarted(
                EXECUTION_ID, SKILL_NAME, PHASE, PHASE_INDEX, TOTAL_PHASES);
        Map<String, Object> payload = parse(json);

        assertEquals("skill.phase_started", payload.get("type"));
        assertEquals(PHASE, payload.get("phase"));
        assertEquals(PHASE_INDEX, ((Number) payload.get("phaseIndex")).intValue());
    }

    // ──────────────────────────── progress ────────────────────────────

    @Test
    void progressEventEscapesModelContent() {
        String chunk = "中文 \"引用\"\n下一行";
        String json = SkillEventFactory.progress(
                EXECUTION_ID, SKILL_NAME, PHASE, PHASE_INDEX, TOTAL_PHASES, chunk);
        Map<String, Object> payload = parse(json);

        assertEquals("skill.progress", payload.get("type"));
        assertEquals(chunk, payload.get("data"));
        assertEquals(PHASE_INDEX, ((Number) payload.get("phaseIndex")).intValue());
    }

    @Test
    void progressEventHandlesNullData() {
        String json = SkillEventFactory.progress(
                EXECUTION_ID, SKILL_NAME, PHASE, PHASE_INDEX, TOTAL_PHASES, null);
        Map<String, Object> payload = parse(json);

        assertEquals("skill.progress", payload.get("type"));
        // data 为 null 时 payload 中不应包含该键
        assertNull(payload.get("data"));
    }

    // ──────────────────────────── phaseComplete ────────────────────────────

    @Test
    void phaseCompleteEventIncludesOutputData() {
        Map<String, Object> output = Map.of("score", 85, "valid", true);
        String json = SkillEventFactory.phaseComplete(
                EXECUTION_ID, SKILL_NAME, PHASE, PHASE_INDEX, TOTAL_PHASES, output);
        Map<String, Object> payload = parse(json);

        assertEquals("skill.phase_complete", payload.get("type"));
        assertNotNull(payload.get("outputData"));
    }

    // ──────────────────────────── complete ────────────────────────────

    @Test
    void completeEventHasStatusAndOutputData() {
        Map<String, Object> result = Map.of("finalContent", "润色后的文章内容");
        String json = SkillEventFactory.complete(EXECUTION_ID, SKILL_NAME, TOTAL_PHASES, result);
        Map<String, Object> payload = parse(json);

        assertEquals("skill.complete", payload.get("type"));
        assertEquals("SUCCESS", payload.get("status"));
        assertEquals(TOTAL_PHASES, ((Number) payload.get("totalPhases")).intValue());
        assertNotNull(payload.get("outputData"));
    }

    // ──────────────────────────── awaitingConfirmation ────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void awaitingConfirmationEventIncludesSupportedActions() {
        Map<String, Object> pending = Map.of("topicOptions", List.of(Map.of("title", "选题A")));
        String json = SkillEventFactory.awaitingConfirmation(
                EXECUTION_ID, SKILL_NAME, PHASE, PHASE_INDEX, TOTAL_PHASES, pending);
        Map<String, Object> payload = parse(json);

        assertEquals("skill.awaiting_confirmation", payload.get("type"));
        assertEquals("AWAITING_CONFIRMATION", payload.get("status"));
        assertEquals(PHASE, payload.get("phase"));

        List<String> actions = (List<String>) payload.get("supportedActions");
        assertNotNull(actions);
        assertTrue(actions.contains("approve"));
        assertTrue(actions.contains("modify"));

        assertNotNull(payload.get("pendingOutput"));
    }

    @Test
    void awaitingConfirmationHandlesNullPendingOutput() {
        String json = SkillEventFactory.awaitingConfirmation(
                EXECUTION_ID, SKILL_NAME, PHASE, PHASE_INDEX, TOTAL_PHASES, null);
        Map<String, Object> payload = parse(json);

        assertEquals("skill.awaiting_confirmation", payload.get("type"));
        assertNull(payload.get("pendingOutput"));
    }

    // ──────────────────────────── error ────────────────────────────

    @Test
    void errorEventIncludesPhaseAndErrorMessage() {
        String json = SkillEventFactory.error(
                EXECUTION_ID, SKILL_NAME, PHASE, "LLM 调用超时");
        Map<String, Object> payload = parse(json);

        assertEquals("skill.error", payload.get("type"));
        assertEquals("FAILED", payload.get("status"));
        assertEquals(PHASE, payload.get("phase"));
        assertEquals("LLM 调用超时", payload.get("errorMessage"));
    }

    @Test
    void errorEventHandlesNullPhase() {
        String json = SkillEventFactory.error(
                EXECUTION_ID, SKILL_NAME, null, "未知错误");
        Map<String, Object> payload = parse(json);

        assertEquals("skill.error", payload.get("type"));
        assertEquals("FAILED", payload.get("status"));
        assertNull(payload.get("phase"));
        assertEquals("未知错误", payload.get("errorMessage"));
    }

    // ──────────────────────────── helpers ────────────────────────────

    private Map<String, Object> parse(String json) {
        return GsonUtils.fromJson(json, new TypeToken<Map<String, Object>>() {});
    }
}
