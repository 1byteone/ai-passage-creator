package com.example.aipassagecreator.skill.parsers;

import com.example.aipassagecreator.model.dto.skill.TopicOption;
import com.example.aipassagecreator.skill.PhaseDefinition;
import com.example.aipassagecreator.skill.SkillOutputParser;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TopicOptionsOutputParser implements SkillOutputParser<List<TopicOption>> {

    @Override
    public List<TopicOption> parse(String llmOutput, PhaseDefinition phase) {
        List<TopicOption> options = GsonUtils.fromJson(
                stripCodeFence(llmOutput),
                new TypeToken<List<TopicOption>>() {
                }
        );
        if (options == null) {
            return List.of();
        }

        List<TopicOption> normalized = new ArrayList<>();
        for (TopicOption option : options) {
            if (option == null || option.getTitle() == null || option.getTitle().isBlank()) {
                continue;
            }
            if (option.getOutline() == null) {
                option.setOutline(new ArrayList<>());
            }
            if (option.getPros() == null) {
                option.setPros(new ArrayList<>());
            }
            if (option.getCons() == null) {
                option.setCons(new ArrayList<>());
            }
            normalized.add(option);
        }
        return normalized;
    }

    @Override
    public String getType() {
        return "topic-options";
    }

    private String stripCodeFence(String output) {
        if (output == null) {
            return "[]";
        }
        String trimmed = output.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }
}
