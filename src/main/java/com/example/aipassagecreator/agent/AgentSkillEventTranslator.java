package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** skill 事件 JSON → agent 事件 JSON（DRY：不复制 skill 事件 schema，只做协议翻译） */
public final class AgentSkillEventTranslator {

    private AgentSkillEventTranslator() {
    }

    /** 返回 agent 事件 JSON；无需桥接的事件返回空字符串 */
    public static String translate(String skillEventJson) {
        if (skillEventJson == null || skillEventJson.isBlank()) {
            return "";
        }
        JsonObject raw;
        try {
            raw = GsonUtils.getInstance().fromJson(skillEventJson, JsonObject.class);
        } catch (Exception e) {
            return "";
        }
        String type = raw.get("type") == null ? "" : raw.get("type").getAsString();
        String requestId = raw.get("skillExecutionId") == null ? "" : raw.get("skillExecutionId").getAsString();
        String skillName = raw.get("skillName") == null ? "" : raw.get("skillName").getAsString();

        return switch (type) {
            case "skill.started" -> AgentEventFactory.skillStarted(requestId, skillName,
                    intOf(raw, "totalPhases"));
            case "skill.phase_started", "skill.progress" -> {
                int phase = intOf(raw, "phaseIndex");
                int total = intOf(raw, "totalPhases");
                String name = raw.get("phase") == null ? "" : raw.get("phase").getAsString();
                int progress = progressOf(raw);
                yield AgentEventFactory.skillPhase(requestId, phase, total, name, progress);
            }
            case "skill.awaiting_confirmation" -> AgentEventFactory.skillConfirm(requestId,
                    requestId, raw.get("phase") == null ? "" : raw.get("phase").getAsString());
            default -> "";
        };
    }

    private static int intOf(JsonObject raw, String key) {
        JsonElement el = raw.get(key);
        return el == null || el.isJsonNull() ? 0 : el.getAsInt();
    }

    /** progress 数据可能是 "40%" 或纯数字，统一解析为 0-100 整数 */
    private static int progressOf(JsonObject raw) {
        JsonElement el = raw.get("data");
        if (el == null || el.isJsonNull()) {
            return 0;
        }
        String text = el.getAsString().replaceAll("[^0-9]", "");
        try {
            return text.isEmpty() ? 0 : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
