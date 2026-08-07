package com.example.aipassagecreator.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.aipassagecreator.agent.agents.TitleGeneratorAgent;
import com.example.aipassagecreator.methodology.MethodologyPromptAssembler;
import com.example.aipassagecreator.service.RagAugmentationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P3 测试 — 标题/大纲阶段参考注入（编排器路径）。
 * <p>验证绑定 RagAugmentationService 后，有 RAG 命中时 agent 把参考写入 graph state。</p>
 */
class TitleOutlineRagInjectionTest {

    private OverAllState stateWith(String topic, Long userId) {
        OverAllState state = mock(OverAllState.class);
        when(state.value("topic")).thenReturn(java.util.Optional.of(topic));
        when(state.value("style")).thenReturn(java.util.Optional.empty());
        when(state.value("methodology")).thenReturn(java.util.Optional.of("default"));
        when(state.value("userId")).thenReturn(userId == null
                ? java.util.Optional.empty() : java.util.Optional.of(userId.toString()));
        return state;
    }

    private ChatResponse titleResponse() {
        // 标题 agent 期望 content 是 JSON 数组；用真实对象构造避免 Mockito 深度 stub
        return mockChatResponse("[{\"mainTitle\":\"主标题\",\"subTitle\":\"副标题\",\"strategyKey\":\"k\"}]");
    }

    private ChatResponse mockChatResponse(String content) {
        org.springframework.ai.chat.messages.AssistantMessage am =
                new org.springframework.ai.chat.messages.AssistantMessage(content);
        Generation gen = new Generation(am);
        return new ChatResponse(List.of(gen));
    }

    @Test
    @DisplayName("P3 — 编排器路径：标题阶段有 RAG 命中时把参考写入 state")
    void titleAgent_withHit_writesReferencesToState() throws Exception {
        ChatResponse chatResponse = titleResponse();
        DashScopeChatModel chatModel = mock(DashScopeChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        MethodologyPromptAssembler assembler = mock(MethodologyPromptAssembler.class);
        when(assembler.buildTitleGuidance(anyString())).thenReturn("");
        RagAugmentationService rag = mock(RagAugmentationService.class);
        RagAugmentationService.Reference ref = new RagAugmentationService.Reference(
                "t1", "历史标题", "历史正文", 0.9, "article");
        when(rag.augment(eq("AI 主题"), eq(1L)))
                .thenReturn(new RagAugmentationService.AugmentedResult(
                        List.of(ref), "\n【参考资料】历史标题"));

        TitleGeneratorAgent agent = new TitleGeneratorAgent(chatModel, assembler, rag);
        Map<String, Object> result = agent.apply(stateWith("AI 主题", 1L));

        assertTrue(result.containsKey("ragReferences"), "命中时应把参考写入 graph state");
        @SuppressWarnings("unchecked")
        List<RagAugmentationService.Reference> refs =
                (List<RagAugmentationService.Reference>) result.get("ragReferences");
        assertTrue(refs.get(0).refId().equals("t1"));
    }

    @Test
    @DisplayName("P3 — 编排器路径：无 RAG 命中时不写 ragReferences")
    void titleAgent_noHit_doesNotWriteReferences() throws Exception {
        ChatResponse chatResponse = titleResponse();
        DashScopeChatModel chatModel = mock(DashScopeChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        MethodologyPromptAssembler assembler = mock(MethodologyPromptAssembler.class);
        when(assembler.buildTitleGuidance(anyString())).thenReturn("");
        RagAugmentationService rag = mock(RagAugmentationService.class);
        when(rag.augment(anyString(), eq(1L)))
                .thenReturn(RagAugmentationService.AugmentedResult.EMPTY);

        TitleGeneratorAgent agent = new TitleGeneratorAgent(chatModel, assembler, rag);
        Map<String, Object> result = agent.apply(stateWith("AI 主题", 1L));

        assertTrue(!result.containsKey("ragReferences"), "无命中时不应写参考");
    }
}