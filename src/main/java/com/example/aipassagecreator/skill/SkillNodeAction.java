package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.aipassagecreator.config.CircuitBreakerConfig;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.skill.tool.WebSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 通用 StateGraph 节点
 * 根据 PhaseDefinition 动态加载 Prompt、调用 LLM、解析输出
 */
@Slf4j
@Component
@Scope("prototype")
public class SkillNodeAction implements NodeAction {

    private final PhaseDefinition phase;
    private final int phaseIndex;
    private final int totalPhases;
    private final PromptTemplateEngine templateEngine;
    private final ModelRouter modelRouter;
    private final OutputParserRegistry parserRegistry;
    /** phase name → outputKey 映射，用于变量引用解析 */
    private final Map<String, String> phaseOutputKeyMap;
    /** 该阶段可用的工具（LLM 工具调用） */
    private final List<ToolCallback> toolCallbacks;
    private final CircuitBreakerConfig breaker;

    public SkillNodeAction(PhaseDefinition phase,
                           int phaseIndex,
                           int totalPhases,
                           PromptTemplateEngine templateEngine,
                           ModelRouter modelRouter,
                           OutputParserRegistry parserRegistry,
                           Map<String, String> phaseOutputKeyMap,
                           List<ToolCallback> toolCallbacks,
                           CircuitBreakerConfig breaker) {
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        this.totalPhases = totalPhases;
        this.templateEngine = templateEngine;
        this.modelRouter = modelRouter;
        this.parserRegistry = parserRegistry;
        this.phaseOutputKeyMap = phaseOutputKeyMap;
        this.toolCallbacks = toolCallbacks == null ? List.of() : toolCallbacks;
        this.breaker = breaker;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String executionId = state.value("skillExecutionId")
                .map(Object::toString).orElseThrow(() ->
                        new IllegalStateException("skillExecutionId 未在 OverAllState 中设置"));

        var ctx = SkillContext.get(executionId);
        if (ctx == null) {
            throw new IllegalStateException("SkillContext 不存在: " + executionId);
        }

        String skillName = state.value("skillName").map(Object::toString).orElse("unknown");
        ctx.setCurrentPhase(phase.getName());
        ctx.setCurrentPhaseIndex(phaseIndex);
        ctx.getPhaseHandler().accept(phase.getName());
        ctx.getStreamHandler().accept(SkillEventFactory.phaseStarted(
                executionId, skillName, phase.getName(), phaseIndex, totalPhases));
        log.info("SkillNodeAction 开始执行: skill={}, phase={}, executionId={}",
                skillName, phase.getName(), executionId);

        // 解析模型
        ChatModel model = modelRouter.resolveWithFallback(
                phase.getModel(),
                state.value("skillDefaultModel").map(Object::toString).orElse(null));

        // 解析输入变量
        Map<String, Object> inputs = resolveInputs(state);

        // 渲染 Prompt
        String prompt = templateEngine.render(phase.getPromptFile(), inputs);
        log.debug("Prompt 渲染完成: phase={}, promptLength={}", phase.getName(), prompt.length());

        // 记录到 AgentLog（modelName 取实际生效的模型，而非阶段声明值）
        String modelName = modelRouter.resolveModelName(
                phase.getModel(),
                state.value("skillDefaultModel").map(Object::toString).orElse(null));
        ctx.getSharedData().put("prompt_" + phase.getName(), prompt);
        ctx.getSharedData().put("model_" + phase.getName(), modelName);
        ctx.recordModelUsed(modelName);

        // 调用 LLM，失败时尝试降级到 fallback 模型。
        // LLM 熔断：连续失败后 fail-fast 直接拒绝，避免反复打已故障的模型；
        // 主/降级任一成功即视为服务可用，仅全部失败才记录失败。
        if (!breaker.allowRequest("llm")) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "LLM 服务暂时不可用，请稍后重试");
        }
        String output;
        int phaseTokens;
        long startTime = System.currentTimeMillis();
        try {
            try {
                if (phase.isStreaming()) {
                    StreamResult streamResult = callStreaming(model, prompt, ctx, executionId, skillName);
                    output = streamResult.text();
                    phaseTokens = streamResult.totalTokens();
                } else {
                    ChatResponse response;
                    if (!toolCallbacks.isEmpty()) {
                        ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                                .toolCallbacks(toolCallbacks)
                                .internalToolExecutionEnabled(true)
                                .build();
                        response = model.call(new Prompt(List.of(new UserMessage(prompt)), toolOptions));
                    } else {
                        response = model.call(new Prompt(new UserMessage(prompt)));
                    }
                    output = response.getResult().getOutput().getText();
                    phaseTokens = extractTotalTokens(response);
                }
            } catch (Exception e) {
                // 主模型失败，切换到配置的 fallback 模型重试（默认 dashscope）
                ChatModel fallbackModel = modelRouter.resolveFallback();
                if (fallbackModel != model) {
                    log.warn("LLM 调用失败，降级到 fallback: phase={}, error={}", phase.getName(), e.getMessage());
                    String fallbackName = modelRouter.resolveModelName(null, null);
                    ctx.recordModelUsed(fallbackName);
                    if (phase.isStreaming()) {
                        StreamResult streamResult = callStreaming(fallbackModel, prompt, ctx, executionId, skillName);
                        output = streamResult.text();
                        phaseTokens = streamResult.totalTokens();
                    } else {
                        ChatResponse response = fallbackModel.call(new Prompt(new UserMessage(prompt)));
                        output = response.getResult().getOutput().getText();
                        phaseTokens = extractTotalTokens(response);
                    }
                } else {
                    throw e; // fallback 与主模型相同，不再重试
                }
            }
            long duration = System.currentTimeMillis() - startTime;
            ctx.addTokenUsage(phaseTokens);
            ctx.getSharedData().put("tokens_" + phase.getName(), phaseTokens);
            log.info("LLM 调用完成: phase={}, model={}, duration={}ms, outputLength={}, tokens={}",
                    phase.getName(), modelName, duration, output.length(), phaseTokens);
            breaker.recordSuccess("llm");
        } catch (Exception e) {
            breaker.recordFailure("llm");
            throw e;
        }

        // 解析输出
        Object parsed = parserRegistry.parse(phase.getOutputParser(), output, phase);
        ctx.getSharedData().put(phase.getOutputKey(), parsed);
        ctx.getStreamHandler().accept(SkillEventFactory.phaseComplete(
                executionId, skillName, phase.getName(), phaseIndex, totalPhases, parsed));

        // 写入 State
        Map<String, Object> result = new HashMap<>();
        result.put(phase.getOutputKey(), parsed);
        result.put(phase.getOutputKey() + "_raw", output);

        return result;
    }

    /** 流式调用结果：拼接后的文本 + 本次请求的 Token 总量 */
    private record StreamResult(String text, int totalTokens) {
    }

    private StreamResult callStreaming(ChatModel model, String prompt, SkillContext.RuntimeContext ctx,
                                       String executionId, String skillName) {
        StringBuilder sb = new StringBuilder();
        Flux<ChatResponse> flux = model.stream(new Prompt(new UserMessage(prompt)));
        AtomicReference<Throwable> error = new AtomicReference<>();
        // 流式响应中多数分片的 usage 为空，仅末尾分片携带整次请求的总量，
        // 因此取最大值而非逐片累加，避免重复计数或取到 0
        AtomicInteger maxTotalTokens = new AtomicInteger(0);

        flux.doOnNext(response -> {
                    int tokens = extractTotalTokens(response);
                    if (tokens > maxTotalTokens.get()) {
                        maxTotalTokens.set(tokens);
                    }
                    String chunk = extractChunkText(response);
                    if (chunk != null) {
                        sb.append(chunk);
                        ctx.getStreamHandler().accept(SkillEventFactory.progress(
                                executionId, skillName, phase.getName(), phaseIndex, totalPhases, chunk));
                    }
                })
                .doOnError(e -> {
                    log.error("流式调用出错: {}", e.getMessage());
                    error.set(e);
                })
                .blockLast();

        if (error.get() != null) {
            throw new RuntimeException("流式 LLM 调用失败", error.get());
        }
        return new StreamResult(sb.toString(), maxTotalTokens.get());
    }

    /**
     * 从 ChatResponse 中提取本次请求的 Token 总量
     * <p>
     * 各层级均可能为 null（尤其流式分片），全部做空值保护，取不到时返回 0。
     */
    private int extractTotalTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return 0;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == null) {
            return 0;
        }
        return usage.getTotalTokens();
    }

    /**
     * 安全提取流式分片文本
     * <p>
     * 携带 usage 的末尾分片通常没有 choices，getResult() 会返回 null。
     */
    private String extractChunkText(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    Map<String, Object> resolveInputs(OverAllState state) {
        Map<String, Object> inputs = new HashMap<>();
        if (phase.getVariables() != null) {
            for (PhaseDefinition.VariableRef varRef : phase.getVariables()) {
                String refKey = varRef.getRef();
                if (refKey != null && !refKey.isEmpty()) {
                    // 将 phase name 映射到 outputKey
                    String actualKey = phaseOutputKeyMap.getOrDefault(refKey, refKey);
                    if (!actualKey.equals(refKey)) {
                        log.debug("变量引用映射: {} -> {}", refKey, actualKey);
                    }
                    // 从上一阶段输出获取
                    Object prevOutput = state.value(actualKey).orElse(null);
                    if (prevOutput != null) {
                        inputs.put(varRef.getName(), prevOutput);
                    }
                } else {
                    // 从全局输入获取
                    state.value(varRef.getName()).ifPresent(v -> inputs.put(varRef.getName(), v));
                }
            }
        }
        return inputs;
    }
}
