package com.example.aipassagecreator.model.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentCreateConversationRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题过长")
    private String title;
}
