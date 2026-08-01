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
        String fixed = GsonUtils.tryFixJson(llmOutput);
        try {
            return GsonUtils.fromJson(fixed, new TypeToken<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", fixed);
        }
    }
}