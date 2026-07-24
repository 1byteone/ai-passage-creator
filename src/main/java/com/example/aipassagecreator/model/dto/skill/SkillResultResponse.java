package com.example.aipassagecreator.model.dto.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResultResponse {
    private String skillExecutionId;
    private String skillName;
    private String status;
    private String phase;
    private Integer durationMs;
    private String errorMessage;
    private Map<String, Object> inputData;
    private Map<String, Object> outputData;
}
