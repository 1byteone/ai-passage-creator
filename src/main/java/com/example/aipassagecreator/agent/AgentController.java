package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.model.dto.agent.AgentChatRequest;
import com.example.aipassagecreator.model.dto.agent.AgentChatResponse;
import com.example.aipassagecreator.model.dto.agent.AgentConversationVO;
import com.example.aipassagecreator.model.dto.agent.AgentCreateConversationRequest;
import com.example.aipassagecreator.model.dto.agent.AgentMessageVO;
import com.example.aipassagecreator.model.vo.LoginUserVO;
import com.example.aipassagecreator.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Agent 创作助手 REST + SSE 端点 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentConversationService conversationService;
    private final UserService userService;
    private final AgentSseEmitterManager sseManager;
    private final AgentRequestRegistry requestRegistry;

    /** 发送消息：登录用户 / 游客均支持（游客仅纯对话，受 GuestRateLimiter 限流） */
    @PostMapping("/chat")
    @RateLimit(limit = 10, window = 60, unit = TimeUnit.SECONDS, key = "agent_chat")
    public BaseResponse<Map<String, Object>> chat(@Valid @RequestBody AgentChatRequest request,
                                                  HttpServletRequest servletRequest) {
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        Long userId = loginUser == null ? null : loginUser.getId();
        String guestId = userId == null ? requireGuestId(request.getGuestId()) : null;

        AgentChatResponse response = conversationService.chat(request, userId, guestId);
        return ResultUtils.success(Map.of(
                "agentRequestId", response.getAgentRequestId(),
                "conversationId", response.getConversationId()));
    }

    /** 订阅 Agent 对话流（SSE）。ownerKey = "u:{userId}" / "g:{guestId}" */
    @GetMapping("/{agentRequestId}/progress")
    public SseEmitter progress(@PathVariable String agentRequestId, HttpServletRequest servletRequest) {
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        String ownerKey = loginUser == null
                ? "g:" + requireGuestId(servletRequest.getParameter("guestId"))
                : "u:" + loginUser.getId();
        if (!requestRegistry.isOwner(agentRequestId, ownerKey)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请求不存在或无权访问");
        }
        return sseManager.subscribe(agentRequestId);
    }

    @GetMapping("/conversations")
    public BaseResponse<List<AgentConversationVO>> conversations(HttpServletRequest servletRequest) {
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        List<AgentConversationVO> list = conversationService.listConversations(loginUser.getId());
        return ResultUtils.success(list);
    }

    @PostMapping("/conversations")
    public BaseResponse<Map<String, Object>> createConversation(@Valid @RequestBody AgentCreateConversationRequest request,
                                                                HttpServletRequest servletRequest) {
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        Long id = conversationService.createConversation(loginUser.getId(), request.getTitle());
        return ResultUtils.success(Map.of("conversationId", id));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public BaseResponse<List<AgentMessageVO>> messages(@PathVariable Long conversationId,
                                                       HttpServletRequest servletRequest) {
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        List<AgentMessageVO> list = conversationService.listMessages(conversationId, loginUser.getId());
        return ResultUtils.success(list);
    }

    private String requireGuestId(String guestId) {
        if (guestId == null || guestId.isBlank() || guestId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "游客请提供 guestId");
        }
        return guestId;
    }
}
