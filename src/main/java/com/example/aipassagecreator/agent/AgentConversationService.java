package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.AgentConversationMapper;
import com.example.aipassagecreator.mapper.AgentMessageMapper;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.dto.agent.AgentChatRequest;
import com.example.aipassagecreator.model.dto.agent.AgentChatResponse;
import com.example.aipassagecreator.model.dto.agent.AgentConversationVO;
import com.example.aipassagecreator.model.dto.agent.AgentMessageVO;
import com.example.aipassagecreator.model.po.AgentConversationPo;
import com.example.aipassagecreator.model.po.AgentMessagePo;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.RagAugmentationService;
import com.example.aipassagecreator.service.UserService;
import com.example.aipassagecreator.skill.ModelRouter;
import com.example.aipassagecreator.skill.SkillExecution;
import com.example.aipassagecreator.skill.SkillExecutionService;
import com.example.aipassagecreator.skill.SkillSseEmitterManager;
import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.utils.GsonUtils;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Agent 对话编排核心：会话持久化 + 意图路由 + 纯对话流式（RAG/skill 桥接由 Task 8/9 挂入） */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConversationService {

    private static final int HISTORY_LIMIT = 20;
    private static final String SYSTEM_PROMPT =
            "你是「AI 创作助手」，服务于 ai-passage-creator 文章创作平台。"
                    + "帮助用户选题、写标题、扩大纲、生成与优化正文。"
                    + "回答简洁、直接，用 markdown 输出。若用户请求创作类任务，可建议其使用对应技能。";

    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentSseEmitterManager sseManager;
    private final AgentRequestRegistry requestRegistry;
    private final ModelRouter modelRouter;
    private final RagAugmentationService ragAugmentationService;
    private final SkillExecutionService skillExecutionService;
    private final SkillSseEmitterManager skillSseEmitterManager;
    private final SkillExecutionMapper skillExecutionMapper;
    private final UserService userService;

    /** requestId → conversationId 映射，供 executeSkillRoute 落库时查找 */
    private final Map<String, Long> requestConversationMap = new ConcurrentHashMap<>();

    // 自身代理：chat() 内必须经 Spring 代理调用 executeChatRoute/executeSkillRoute，
    // 直接 this.xxx() 是自调用，会绕过 @Async 拦截导致流式生成阻塞在 HTTP 请求线程。
    // @Lazy 打破自我引用环，仅注入懒解析代理；@Autowired 是字段注入的必需标记。
    @Autowired
    @Lazy
    private AgentConversationService self;

    @Transactional(rollbackFor = Exception.class)
    public Long createConversation(Long userId, String title) {
        AgentConversationPo po = AgentConversationPo.builder()
                .userId(userId).title(title)
                .updateTime(LocalDateTime.now())
                .build();
        conversationMapper.insert(po);
        // createTime 由 DB CURRENT_TIMESTAMP 回填，insert 后 id 自动赋值
        return po.getId();
    }

    public List<AgentConversationVO> listConversations(Long userId) {
        List<AgentConversationPo> list = conversationMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId).eq("is_delete", 0)
                        .orderBy("update_time", false));
        return list.stream().map(po -> AgentConversationVO.builder()
                .id(po.getId()).title(po.getTitle())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime())
                .build()).toList();
    }

    public List<AgentMessageVO> listMessages(Long conversationId, Long userId) {
        checkConversationOwner(conversationId, userId);
        List<AgentMessagePo> list = messageMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("conversation_id", conversationId).eq("is_delete", 0)
                        .orderBy("create_time", true));
        return list.stream().map(this::toVo).toList();
    }

    /**
     * 发送消息 → 建请求 → 路由（显式 skill / 关键词 / 纯对话）→ 异步执行。
     * 返回 agentRequestId 供前端订阅 SSE。
     */
    public AgentChatResponse chat(AgentChatRequest req, Long userId, String guestId) {
        String ownerKey = userId != null ? "u:" + userId : "g:" + guestId;
        String requestId = "agent-" + UUID.randomUUID().toString().substring(0, 8);
        requestRegistry.register(requestId, ownerKey);

        Long conversationId = null;
        if (userId != null) {
            conversationId = resolveConversation(req, userId);
            appendMessage(conversationId, "user", "text", req.getMessage(), null);
            requestConversationMap.put(requestId, conversationId);
        }

        // 路由：显式 skill 优先，其次关键词意图，最后纯对话。
        // 关键词意图仅对登录用户生效——游客 (userId=null) 不触发 skill。
        // 经 self 代理调用以触发 @Async("skillExecutor")，chat() 立即返回 agentRequestId 供前端订阅 SSE
        if (req.getSkillName() != null && !req.getSkillName().isBlank()) {
            self.executeSkillRoute(requestId, req.getSkillName(), req.getInputs(), userId);
        } else {
            String detected = AgentSkillIntentDetector.detect(req.getMessage());
            if (detected != null && userId != null) {
                self.executeSkillRoute(requestId, detected, null, userId);
            } else {
                self.executeChatRoute(requestId, req.getMessage(), conversationId, userId);
            }
        }

        return AgentChatResponse.builder()
                .agentRequestId(requestId)
                .conversationId(conversationId)
                .build();
    }

    private Long resolveConversation(AgentChatRequest req, Long userId) {
        if (req.getConversationId() != null) {
            checkConversationOwner(req.getConversationId(), userId);
            return req.getConversationId();
        }
        String title = req.getMessage().length() > 20
                ? req.getMessage().substring(0, 20) : req.getMessage();
        return createConversation(userId, title);
    }

    private void checkConversationOwner(Long conversationId, Long userId) {
        AgentConversationPo po = conversationMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", conversationId).eq("is_delete", 0));
        if (po == null || !po.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "会话不存在或无权访问");
        }
    }

    /** 持久化一条消息，返回其 id */
    private Long appendMessage(Long conversationId, String role, String kind, String content, String metaJson) {
        AgentMessagePo po = AgentMessagePo.builder()
                .conversationId(conversationId).role(role).kind(kind)
                .content(content == null ? "" : content).metaJson(metaJson)
                .build();
        messageMapper.insert(po);
        return po.getId();
    }

    /** 纯对话路由 — RAG 增强 + 流式 + 落库 */
    @Async("skillExecutor")
    public void executeChatRoute(String requestId, String userMessage,
                                 Long conversationId, Long userId) {
        sseManager.publish(requestId, AgentEventFactory.started(requestId));
        try {
            // RAG 增强：命中才注入参考块并推送引用事件；userId null（游客）自动跳过
            var augmented = ragAugmentationService.augment(userMessage, userId);
            if (!augmented.isEmpty()) {
                var sources = augmented.references().stream()
                        .map(r -> new AgentEventFactory.SourceRef(
                                r.refId(), r.title(), r.type(), "最近"))
                        .toList();
                sseManager.publish(requestId, AgentEventFactory.ragReference(requestId, sources));
            }

            ChatModel chatModel = modelRouter.resolve(null, null);
            List<Message> messages = buildMessages(conversationId, userId, userMessage);
            // 注入软参考块（system 消息里追加）
            if (!augmented.isEmpty()) {
                messages.add(new SystemMessage(augmented.promptBlock()));
            }

            StringBuilder full = new StringBuilder();
            for (ChatResponse resp : chatModel.stream(new Prompt(messages)).toIterable()) {
                String delta = resp.getResult().getOutput().getText();
                if (delta != null && !delta.isEmpty()) {
                    full.append(delta);
                    sseManager.publish(requestId, AgentEventFactory.textDelta(requestId, delta));
                }
            }
            Long msgId = null;
            if (conversationId != null) {
                msgId = appendMessage(conversationId, "assistant", "text", full.toString(), null);
            }
            sseManager.publish(requestId, AgentEventFactory.complete(requestId, msgId));
        } catch (Exception e) {
            log.error("Agent 对话失败: requestId={}", requestId, e);
            sseManager.publish(requestId, AgentEventFactory.error(requestId, "生成失败，请重试"));
        } finally {
            sseManager.complete(requestId);
            requestRegistry.remove(requestId);
        }
    }

    /** 组装会话历史（最近 HISTORY_LIMIT 条） + 当前用户消息 */
    private List<Message> buildMessages(Long conversationId, Long userId, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        if (conversationId != null) {
            List<AgentMessagePo> history = messageMapper.selectListByQuery(
                    QueryWrapper.create()
                            .eq("conversation_id", conversationId).eq("is_delete", 0)
                            .orderBy("create_time", false)
                            .limit(HISTORY_LIMIT));
            // 倒序翻转回正序，去掉刚插入的当前用户消息（避免重复）
            for (int i = history.size() - 1; i >= 0; i--) {
                AgentMessagePo m = history.get(i);
                if (m.getContent().equals(userMessage) && "user".equals(m.getRole())) {
                    continue;
                }
                messages.add(m.getRole().equals("user")
                        ? new UserMessage(m.getContent())
                        : new AgentAssistantMessage(m.getContent()));
            }
        }
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    /** skill 路由 — 桥接 skill 执行事件到 agent SSE 流 + 落库 skill 结果 */
    @Async("skillExecutor")
    void executeSkillRoute(String requestId, String skillName, Map<String, Object> inputs, Long userId) {
        sseManager.publish(requestId, AgentEventFactory.started(requestId));
        try {
            User user = userId != null ? userService.getById(userId) : null;
            if (user == null) {
                sseManager.publish(requestId, AgentEventFactory.error(requestId, "用户不存在"));
                return;
            }
            SkillExecution execution = skillExecutionService.dispatchAndExecute(
                    skillName, inputs == null ? Map.of() : inputs, user);

            // 桥接 skill 事件进 agent 流（listen 会先回放缓冲，后实时派发）
            skillSseEmitterManager.listen(execution.getExecutionId(),
                    event -> {
                        String agentEvent = AgentSkillEventTranslator.translate(event);
                        if (!agentEvent.isEmpty()) {
                            sseManager.publish(requestId, agentEvent);
                        }
                    });

            // 等 skill 到终态：轮询执行状态（最多 10 分钟），随后落库 assistant 消息
            waitTerminal(execution.getExecutionId());
            Map<String, Object> output = fetchSkillOutput(execution.getExecutionId());
            Long msgId = null;
            if (userId != null && output != null) {
                String content = String.valueOf(output.getOrDefault("output", ""));
                Long conversationId = conversationIdOf(requestId);
                if (conversationId != null) {
                    msgId = appendMessage(conversationId, "assistant", "skill", content,
                            "{\"executionId\":\"%s\"}".formatted(execution.getExecutionId()));
                }
            }
            sseManager.publish(requestId, AgentEventFactory.complete(requestId, msgId));
        } catch (Exception e) {
            log.error("Agent skill 路由失败: requestId={}, skillName={}", requestId, skillName, e);
            sseManager.publish(requestId, AgentEventFactory.error(requestId, "技能执行失败，请重试"));
        } finally {
            sseManager.complete(requestId);
            requestRegistry.remove(requestId);
        }
    }

    private void waitTerminal(String executionId) {
        for (int i = 0; i < 120; i++) {           // 10min / 5s
            SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                    QueryWrapper.create().eq("skill_execution_id", executionId));
            if (po != null && (SkillExecutionStatusEnum.SUCCESS.getValue().equals(po.getStatus())
                    || SkillExecutionStatusEnum.FAILED.getValue().equals(po.getStatus()))) {
                return;
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Map<String, Object> fetchSkillOutput(String executionId) {
        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", executionId));
        if (po == null || po.getOutputData() == null || po.getOutputData().isBlank()) {
            return Map.of();
        }
        return GsonUtils.getInstance().fromJson(po.getOutputData(),
                new com.google.gson.reflect.TypeToken<Map<String, Object>>() {
                });
    }

    /** 从 requestId 查找对应的 conversationId（chat() 时已注册） */
    private Long conversationIdOf(String requestId) {
        return requestConversationMap.get(requestId);
    }

    private AgentMessageVO toVo(AgentMessagePo po) {
        return AgentMessageVO.builder()
                .id(po.getId()).role(po.getRole()).kind(po.getKind())
                .content(po.getContent()).metaJson(po.getMetaJson())
                .createTime(po.getCreateTime())
                .build();
    }
}