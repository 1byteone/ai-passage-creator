package com.example.aipassagecreator.mapper;

import com.example.aipassagecreator.model.po.AgentConversationPo;
import com.example.aipassagecreator.model.po.AgentMessagePo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AgentMapperTest {

    @Resource
    private AgentConversationMapper conversationMapper;
    @Resource
    private AgentMessageMapper messageMapper;

    @Test
    void conversation_insertAndSelect_roundtrip() {
        AgentConversationPo po = AgentConversationPo.builder()
                .userId(1L).title("测试会话").build();
        conversationMapper.insert(po);
        assertNotNull(po.getId());

        AgentConversationPo got = conversationMapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create().eq("id", po.getId()));
        assertNotNull(got);
        assertEquals("测试会话", got.getTitle());
    }

    @Test
    void message_insertAndListByConversation_ok() {
        AgentConversationPo conv = AgentConversationPo.builder()
                .userId(1L).title("测试会话2").build();
        conversationMapper.insert(conv);

        messageMapper.insert(AgentMessagePo.builder()
                .conversationId(conv.getId()).role("user").kind("text").content("你好").build());
        messageMapper.insert(AgentMessagePo.builder()
                .conversationId(conv.getId()).role("assistant").kind("text").content("你好，我是创作助手").build());

        var list = messageMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .eq("conversation_id", conv.getId())
                        .eq("is_delete", 0)
                        .orderBy("create_time", true));
        assertEquals(2, list.size());
        assertEquals("user", list.get(0).getRole());
    }
}
