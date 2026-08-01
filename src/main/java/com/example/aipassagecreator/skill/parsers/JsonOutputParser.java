package com.example.aipassagecreator.skill.parsers;

import com.example.aipassagecreator.skill.PhaseDefinition;
import com.example.aipassagecreator.skill.SkillOutputParser;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class JsonOutputParser implements SkillOutputParser<Map<String, Object>> {

    @Override
    public String getType() { return "json"; }

    @Override
    public Map<String, Object> parse(String llmOutput, PhaseDefinition phase) {
        String fixed = tryFixJson(llmOutput);
        try {
            return GsonUtils.fromJson(fixed, new TypeToken<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("JSON 解析失败，返回原始文本: {}", e.getMessage());
            return Map.of("raw", fixed);
        }
    }

    /**
     * 修复被截断的 JSON：补全未闭合的大括号/方括号/引号
     * 复用现有 tryFixJson 算法逻辑
     */
    private String tryFixJson(String json) {
        if (json == null || json.isBlank()) return "{}";
        String trimmed = json.trim();
        if (!trimmed.startsWith("{")) return trimmed;

        StringBuilder sb = new StringBuilder(trimmed);
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;

        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '"' && (i == 0 || sb.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') braceCount++;
                if (c == '}') braceCount--;
                if (c == '[') bracketCount++;
                if (c == ']') bracketCount--;
            }
        }

        if (inString) sb.append('"');
        while (bracketCount > 0) { sb.append(']'); bracketCount--; }
        while (braceCount > 0) { sb.append('}'); braceCount--; }

        String result = sb.toString();
        log.debug("JSON 修复: 原始长度={}, 修复后长度={}", trimmed.length(), result.length());
        return result;
    }
}