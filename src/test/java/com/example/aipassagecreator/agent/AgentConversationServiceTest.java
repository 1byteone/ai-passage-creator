package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.mapper.AgentConversationMapper;
import com.example.aipassagecreator.mapper.AgentMessageMapper;
import com.example.aipassagecreator.model.dto.agent.AgentChatRequest;
import com.example.aipassagecreator.model.dto.agent.AgentChatResponse;
import com.example.aipassagecreator.model.po.AgentConversationPo;
import com.example.aipassagecreator.skill.ModelRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentConversationServiceTest {

    @Mock
    private AgentConversationMapper conversationMapper;
    @Mock
    private AgentMessageMapper messageMapper;
    @Mock
    private AgentSseEmitterManager sseManager;
    @Mock
    private AgentRequestRegistry requestRegistry;
    @Mock
    private ModelRouter modelRouter;

    @InjectMocks
    private AgentConversationService service;

    @Test
    void createConversation_setsUserAndTitle() {
        // MyBatis-Flex KeyType.Auto 在真实 insert 后回填 id；mock 需模拟该行为
        doAnswer(inv -> {
            AgentConversationPo po = inv.getArgument(0);
            po.setId(100L);
            return 1;
        }).when(conversationMapper).insert(any(AgentConversationPo.class));
        Long id = service.createConversation(7L, "我的新会话");
        assertNotNull(id);
        assertEquals(100L, id);
        verify(conversationMapper).insert(any(AgentConversationPo.class));
    }

    @Test
    void chat_guestNoKeyword_routesToChat() {
        AgentChatRequest req = new AgentChatRequest();
        req.setMessage("你好，介绍一下你自己");
        req.setGuestId("g1");
        // mock 流式：返回空 Flux，避免真实调用聊天模型
        when(modelRouter.resolve(null, null)).thenReturn(mock(ChatModel.class, invocation ->
                "stream".equals(invocation.getMethod().getName())
                        ? Flux.empty() : null));
        AgentChatResponse resp = service.chat(req, null, "g1");
        assertNotNull(resp.getAgentRequestId());
        assertNull(resp.getConversationId()); // 游客不建会话
        verify(requestRegistry).register(any(String.class), eq("g:g1"));
    }

    @Test
    void detectIntent_keywordMapsToSkill() {
        assertEquals("content-summarizer", AgentSkillIntentDetector.detect("帮我总结这篇文章"));
        assertEquals("rewrite-plagiarism", AgentSkillIntentDetector.detect("帮我改写降重这段"));
        assertNull(AgentSkillIntentDetector.detect("你好"));
    }
}