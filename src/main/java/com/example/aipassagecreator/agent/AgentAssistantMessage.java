package com.example.aipassagecreator.agent;

import org.springframework.ai.chat.messages.MessageType;

import java.util.Map;

/**
 * assistant 角色的极简 Message 实现。
 * Spring AI 的 AssistantMessage 构造需要 text block 等重载，多行纯文本回调时直接构造不便，
 * 这里复用 AbstractMessage 的 (MessageType, text, metadata) 构造器组织历史 assistant 消息。
 */
public class AgentAssistantMessage extends org.springframework.ai.chat.messages.AbstractMessage {

    public AgentAssistantMessage(String content) {
        super(MessageType.ASSISTANT, content, Map.of());
    }
}