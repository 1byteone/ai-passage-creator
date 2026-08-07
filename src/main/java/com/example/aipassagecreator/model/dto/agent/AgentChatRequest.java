package com.example.aipassagecreator.model.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** 发送一条消息给 Agent（POST /api/agent/chat） */
@Data
public class AgentChatRequest {

    /** 会话 ID；游客或新会话为空（登录后自动建会话） */
    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息过长")
    private String message;

    /** 显式指定要触发的 skill（手动兜底 tab），为空则自动路由 */
    private String skillName;

    /** skill 输入参数（显式触发时使用） */
    private Map<String, Object> inputs;

    /** 游客 ID（未登录时必填，前端 localStorage 生成） */
    private String guestId;
}
