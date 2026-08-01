package com.example.aipassagecreator.skill.parsers;

import com.example.aipassagecreator.skill.PhaseDefinition;
import com.example.aipassagecreator.skill.SkillOutputParser;
import org.springframework.stereotype.Component;

@Component
public class MarkdownOutputParser implements SkillOutputParser<String> {

    @Override
    public String getType() { return "markdown"; }

    @Override
    public String parse(String llmOutput, PhaseDefinition phase) {
        return llmOutput; // 直接返回 Markdown 文本
    }
}