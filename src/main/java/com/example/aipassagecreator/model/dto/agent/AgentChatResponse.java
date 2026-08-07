package com.example.aipassagecreator.model.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentChatResponse {

    /** 本次请求流式推送 ID（GET /agent/{id}/progress 订阅） */
    private String agentRequestId;

    /** 会话 ID（登录用户；游客为 null） */
    private Long conversationId;
}
