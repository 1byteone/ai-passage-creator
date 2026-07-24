package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
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

    public SkillNodeAction(PhaseDefinition phase,
                           int phaseIndex,
                           int totalPhases,
                           PromptTemplateEngine templateEngine,
                           ModelRouter modelRouter,
                           OutputParserRegistry parserRegistry,
                           Map<String, String> phaseOutputKeyMap) {
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        this.totalPhases = totalPhases;
        this.templateEngine = templateEngine;
        this.modelRouter = modelRouter;
        this.parserRegistry = parserRegistry;
        this.phaseOutputKeyMap = phaseOutputKeyMap;
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

        // 记录到 AgentLog
        String modelName = phase.getModel() != null ? phase.getModel() : "default";
        ctx.getSharedData().put("prompt_" + phase.getName(), prompt);
        ctx.getSharedData().put("model_" + phase.getName(), modelName);

        // 调用 LLM
        String output;
        long startTime = System.currentTimeMillis();
        if (phase.isStreaming()) {
            output = callStreaming(model, prompt, ctx, executionId, skillName);
        } else {
            output = callNonStreaming(model, prompt);
        }
        long duration = System.currentTimeMillis() - startTime;
        log.info("LLM 调用完成: phase={}, duration={}ms, outputLength={}",
                phase.getName(), duration, output.length());

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

    private String callNonStreaming(ChatModel model, String prompt) {
        ChatResponse response = model.call(new Prompt(new UserMessage(prompt)));
        return response.getResult().getOutput().getText();
    }

    private String callStreaming(ChatModel model, String prompt, SkillContext.RuntimeContext ctx,
                                 String executionId, String skillName) {
        StringBuilder sb = new StringBuilder();
        Flux<ChatResponse> flux = model.stream(new Prompt(new UserMessage(prompt)));
        AtomicReference<Throwable> error = new AtomicReference<>();

        flux.doOnNext(response -> {
                    String chunk = response.getResult().getOutput().getText();
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
        return sb.toString();
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
