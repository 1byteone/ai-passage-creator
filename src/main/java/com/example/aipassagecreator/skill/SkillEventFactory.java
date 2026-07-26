package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.utils.GsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SkillEventFactory {

    private SkillEventFactory() {
    }

    public static String started(String executionId, String skillName, int totalPhases) {
        return event("skill.started", executionId, skillName, null, null, totalPhases, null, null);
    }

    public static String phaseStarted(String executionId, String skillName, String phase,
                                      int phaseIndex, int totalPhases) {
        return event("skill.phase_started", executionId, skillName, phase, phaseIndex, totalPhases, null, null);
    }

    public static String progress(String executionId, String skillName, String phase,
                                  int phaseIndex, int totalPhases, String data) {
        return event("skill.progress", executionId, skillName, phase, phaseIndex, totalPhases, data, null);
    }

    public static String phaseComplete(String executionId, String skillName, String phase,
                                       int phaseIndex, int totalPhases, Object outputData) {
        return event("skill.phase_complete", executionId, skillName, phase,
                phaseIndex, totalPhases, null, outputData);
    }

    public static String complete(String executionId, String skillName, int totalPhases,
                                  Map<String, Object> outputData) {
        Map<String, Object> payload = base("skill.complete", executionId, skillName);
        payload.put("status", "SUCCESS");
        payload.put("totalPhases", totalPhases);
        payload.put("outputData", outputData);
        return GsonUtils.toJson(payload);
    }

    /**
     * 执行已暂停，等待用户确认
     *
     * @param phase          即将执行、需要确认的阶段
     * @param pendingOutput  待用户审阅的上一阶段产出
     */
    public static String awaitingConfirmation(String executionId, String skillName, String phase,
                                              int phaseIndex, int totalPhases, Object pendingOutput) {
        Map<String, Object> payload = base("skill.awaiting_confirmation", executionId, skillName);
        payload.put("status", "AWAITING_CONFIRMATION");
        payload.put("phase", phase);
        payload.put("phaseIndex", phaseIndex);
        payload.put("totalPhases", totalPhases);
        // 前端据此渲染确认面板；approve 直接续跑，modify 可回传修改后的数据
        payload.put("supportedActions", java.util.List.of("approve", "modify"));
        if (pendingOutput != null) {
            payload.put("pendingOutput", pendingOutput);
        }
        return GsonUtils.toJson(payload);
    }

    public static String error(String executionId, String skillName, String phase, String errorMessage) {
        Map<String, Object> payload = base("skill.error", executionId, skillName);
        payload.put("status", "FAILED");
        if (phase != null) {
            payload.put("phase", phase);
        }
        payload.put("errorMessage", errorMessage);
        return GsonUtils.toJson(payload);
    }

    private static String event(String type, String executionId, String skillName, String phase,
                                Integer phaseIndex, Integer totalPhases, String data, Object outputData) {
        Map<String, Object> payload = base(type, executionId, skillName);
        if (phase != null) {
            payload.put("phase", phase);
        }
        if (phaseIndex != null) {
            payload.put("phaseIndex", phaseIndex);
        }
        if (totalPhases != null) {
            payload.put("totalPhases", totalPhases);
        }
        if (data != null) {
            payload.put("data", data);
        }
        if (outputData != null) {
            payload.put("outputData", outputData);
        }
        return GsonUtils.toJson(payload);
    }

    private static Map<String, Object> base(String type, String executionId, String skillName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("skillExecutionId", executionId);
        payload.put("skillName", skillName);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }
}
