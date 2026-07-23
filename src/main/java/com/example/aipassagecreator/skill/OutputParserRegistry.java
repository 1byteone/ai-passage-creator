package com.example.aipassagecreator.skill;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OutputParserRegistry {

    private final Map<String, SkillOutputParser<?>> parserMap = new HashMap<>();

    @PostConstruct
    public void init(List<SkillOutputParser<?>> parsers) {
        for (SkillOutputParser<?> parser : parsers) {
            parserMap.put(parser.getType(), parser);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T parse(String type, String llmOutput, PhaseDefinition phase) {
        SkillOutputParser<?> parser = parserMap.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("未知解析器类型: " + type + "，可用类型: " + parserMap.keySet());
        }
        return (T) parser.parse(llmOutput, phase);
    }

    /** 获取所有已注册的解析器类型 */
    public Map<String, SkillOutputParser<?>> getRegisteredParsers() {
        return Map.copyOf(parserMap);
    }
}