package com.example.aipassagecreator.model.dto.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillExecuteResponse {
    private String skillExecutionId;
    private String skillName;
    private String status;
    private int totalPhases;
    private String progressUrl;
}