package com.example.aipassagecreator.model.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentMessageVO {

    private Long id;
    private String role;
    private String kind;
    private String content;
    private String metaJson;
    private LocalDateTime createTime;
}
