package com.example.aipassagecreator.agent.agents;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.aipassagecreator.constant.PromptConstant;
import com.example.aipassagecreator.agent.context.StreamHandlerContext;
import com.example.aipassagecreator.enums.ArticleStyleEnum;
import com.example.aipassagecreator.methodology.MethodologyPromptAssembler;
import com.example.aipassagecreator.methodology.antiai.AntiAiFlavorRules;
import com.example.aipassagecreator.enums.SseMessageTypeEnum;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.service.RagAugmentationService;
import com.example.aipassagecreator.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 正文生成 Agent
 * 根据大纲生成文章正文内容（支持流式输出）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ContentGeneratorAgent implements NodeAction {
    private final DashScopeChatModel chatModel;
    private final MethodologyPromptAssembler methodologyPromptAssembler;
    private final RagAugmentationService ragAugmentationService;

    public static final String INPUT_MAIN_TITLE = "mainTitle";
    public static final String INPUT_SUB_TITLE = "subTitle";
    public static final String INPUT_OUTLINE = "outline";
    public static final String INPUT_STYLE = "style";
    public static final String INPUT_METHODOLOGY = "methodology";
    public static final String INPUT_USER_ID = "userId";
    public static final String OUTPUT_CONTENT = "content";
    /** RAG 参考溯源：把注入的参考列表写入 graph state，供编排器收集存库/SSE */
    public static final String KEY_RAG_REFERENCES = "ragReferences";


    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE)
                .map(Object::toString)
                .orElseThrow(()-> new IllegalArgumentException("缺少主标题参数"));

        String subTitle = state.value(INPUT_SUB_TITLE)
                .map(Object::toString)
                .orElse("");

        @SuppressWarnings("unchecked")
        ArticleState.OutlineResult outlineResult = state.value(INPUT_OUTLINE)
                .map(v ->{
                    if(v instanceof ArticleState.OutlineResult){
                        return (ArticleState.OutlineResult) v;
                    }
                    return GsonUtils.fromJson(GsonUtils.toJson(v), ArticleState.OutlineResult.class);
                })
                .orElseThrow(()-> new IllegalArgumentException("缺少大纲参数"));

        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);

        log.info("ContentGeneratorAgent 开始执行, mainTitle={}", mainTitle);

        //构建prompt
        String outlineText = GsonUtils.toJson(outlineResult.getSections());
        String methodology = state.value(INPUT_METHODOLOGY).map(Object::toString).orElse("default");
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{outlineText}", outlineText)
                + getStylePrompt(style)
                + methodologyPromptAssembler.buildContentGuidance(methodology)
                + (methodology == null || methodology.isBlank()
                ? AntiAiFlavorRules.CONTENT_QUALITY_GUIDANCE : "");

        // RAG 参考增强：以主标题+大纲章节作 query，检索该用户历史文章/共享文档作软参考
        Long userId = state.value(INPUT_USER_ID).map(v -> Long.valueOf(v.toString())).orElse(null);
        String ragQuery = mainTitle + (subTitle == null || subTitle.isBlank() ? "" : " " + subTitle)
                + " " + outlineTitles(outlineResult);
        RagAugmentationService.AugmentedResult augmented = ragAugmentationService.augment(ragQuery, userId);
        if (!augmented.isEmpty()) {
            log.info("ContentGeneratorAgent 注入 RAG 参考 {} 条, taskId={}", augmented.references().size(), userId);
            prompt += augmented.promptBlock();
        }

        //获取流式处理器
        Consumer<String > streamHandler = StreamHandlerContext.get();

        //调用LLM(流式输出)
        String content = callLlmWithStreaming(prompt, streamHandler);

        log.info("ContentGeneratorAgent 执行完成, 正文长度={}", content.length());

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put(OUTPUT_CONTENT, content);
        if (!augmented.isEmpty()) {
            result.put(KEY_RAG_REFERENCES, augmented.references());
        }
        return result;
    }

    /** 汇总大纲各章节标题，作为正文阶段 RAG 检索 query 的一部分 */
    private String outlineTitles(ArticleState.OutlineResult outline) {
        if (outline == null || outline.getSections() == null || outline.getSections().isEmpty()) {
            return "";
        }
        return outline.getSections().stream()
                .map(ArticleState.OutlineSection::getTitle)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" "));
    }

    /**
     * 调用 LLM(流式输出)
     */
    private String callLlmWithStreaming(String prompt, Consumer<String> streamHandler) {
        StringBuilder contentBuilder = new StringBuilder();

        Flux<ChatResponse> streamResponse = chatModel.stream(new Prompt(new UserMessage(prompt)));

        streamResponse
                .doOnNext(response -> {
                    String chunk = response.getResult().getOutput().getText();
                    if(chunk!=null&& !chunk.isEmpty()){
                        contentBuilder.append(chunk);
                        //带前缀发送流式消息
                        if(streamHandler!=null){
                            streamHandler.accept(SseMessageTypeEnum.AGENT3_STREAMING.getStreamingPrefix()+ chunk);
                        }
                    }
                })
                .doOnError(error -> log.error("ContentGeneratorAgent 流式输出错误: {}", error.getMessage()))
                .blockLast();

        return contentBuilder.toString();
    }

    /**
     * 根据风格获取对应的Prompt 附加内容
     */
    private String getStylePrompt(String style){
        if(style == null || style.isEmpty()){
            return "";
        }
        ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
        if(styleEnum == null){
            return "";
        }

        return switch (styleEnum){
            case TECH -> PromptConstant.STYLE_TECH_PROMPT;
            case EMOTIONAL -> PromptConstant.STYLE_EMOTIONAL_PROMPT;
            case EDUCATIONAL -> PromptConstant.STYLE_EDUCATIONAL_PROMPT;
            case HUMOROUS -> PromptConstant.STYLE_HUMOROUS_PROMPT;
        };
    }
}
