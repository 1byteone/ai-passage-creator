package com.example.aipassagecreator.model.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentConversationVO {

    private Long id;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
