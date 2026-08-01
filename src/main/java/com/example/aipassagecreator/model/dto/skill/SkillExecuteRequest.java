package com.example.aipassagecreator.model.dto.skill;

import lombok.Data;
import java.util.Map;

@Data
public class SkillExecuteRequest {
    private String skillName;
    private Map<String, Object> inputs;
}