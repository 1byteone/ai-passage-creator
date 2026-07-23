package com.example.aipassagecreator.model.dto.skill;

import lombok.Data;

@Data
public class SkillConfirmRequest {
    private String executionId;
    private String phase;
    private String action; // approve / retry / modify
    private String modifiedData;
}