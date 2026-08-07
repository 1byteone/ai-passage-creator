package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.model.dto.agent.AgentChatRequest;
import com.example.aipassagecreator.model.dto.agent.AgentChatResponse;
import com.example.aipassagecreator.model.dto.agent.AgentCreateConversationRequest;
import com.example.aipassagecreator.model.dto.agent.AgentMessageVO;
import com.example.aipassagecreator.model.vo.LoginUserVO;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentControllerTest {

    private AgentController controller;
    private AgentConversationService service;
    private UserService userService;
    private AgentSseEmitterManager sseManager;
    private AgentRequestRegistry registry;
    private GuestRateLimiter guestRateLimiter;

    @BeforeEach
    void setUp() {
        service = mock(AgentConversationService.class);
        userService = mock(UserService.class);
        sseManager = mock(AgentSseEmitterManager.class);
        registry = new AgentRequestRegistry();
        guestRateLimiter = new GuestRateLimiter(5);
        controller = new AgentController(service, userService, sseManager, registry, guestRateLimiter);
    }

    @Test
    void chat_loginUser_returnsAgentRequestId() {
        LoginUserVO loginUser = new LoginUserVO();
        loginUser.setId(7L);
        when(userService.getLoginUserVO(any())).thenReturn(loginUser);
        when(service.chat(any(), eq(7L), isNull())).thenReturn(
                AgentChatResponse.builder().agentRequestId("agent-abc").conversationId(1L).build());

        AgentChatRequest req = new AgentChatRequest();
        req.setMessage("你好");
        @SuppressWarnings("unchecked")
        BaseResponse<Map<String, Object>> resp = (BaseResponse<Map<String, Object>>) controller.chat(
                req, new MockHttpServletRequest());

        assertEquals(0, resp.getCode());
        assertNotNull(resp.getData());
        assertEquals("agent-abc", resp.getData().get("agentRequestId"));
    }

    @Test
    void progress_guestOwnerForbidden_throws() {
        // register under a real guest, then try with a different guestId → NOT_FOUND
        registry.register("agent-x", "g:real-guest");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter("guestId", "wrong-guest");
        when(userService.getLoginUserVO(req)).thenReturn(null);
        try {
            controller.progress("agent-x", req);
            fail("expected BusinessException");
        } catch (BusinessException e) {
            assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), e.getCode());
        }
    }

    @Test
    void progress_ownerOk_returnsEmitter() {
        registry.register("agent-y", "u:1");
        MockHttpServletRequest req = new MockHttpServletRequest();
        LoginUserVO loginUser = new LoginUserVO();
        loginUser.setId(1L);
        when(userService.getLoginUserVO(req)).thenReturn(loginUser);
        when(sseManager.subscribe("agent-y")).thenReturn(new SseEmitter());
        assertNotNull(controller.progress("agent-y", req));
    }
}
