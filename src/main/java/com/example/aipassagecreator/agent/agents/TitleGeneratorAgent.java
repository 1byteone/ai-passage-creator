package com.example.aipassagecreator.agent.agents;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.aipassagecreator.constant.PromptConstant;
import com.example.aipassagecreator.enums.ArticleStyleEnum;
import com.example.aipassagecreator.methodology.MethodologyPromptAssembler;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.service.RagAugmentationService;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 标题生成 Agent
 * 根据选题生成 3 ~ 5个爆款标题方案
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TitleGeneratorAgent implements NodeAction {

    private final DashScopeChatModel chatModel;
    private final MethodologyPromptAssembler methodologyPromptAssembler;
    private final RagAugmentationService ragAugmentationService;

    public static final String INPUT_TOPIC = "topic";
    public static final String INPUT_METHODOLOGY = "methodology";
    private static final String INPUT_STYLE = "style";
    private static final String INPUT_USER_ID = "userId";
    private static final String OUTPUT_TITLE_OPTIONS = "titleOptions";
    /** RAG 参考：把注入的参考列表写入 graph state，供编排器收集存库/SSE */
    private static final String KEY_RAG_REFERENCES = "ragReferences";


    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String topic = state.value(INPUT_TOPIC)
                .map(Object::toString)
                .orElseThrow(()-> new IllegalArgumentException("缺少选题参数"));

        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);

        log.info("TitleGeneratorAgent 开始执行标：topic={},style={}", topic, style);

        //构建prompt
        String methodology = state.value(INPUT_METHODOLOGY).map(Object::toString).orElse("default");
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT
                .replace("{topic}", topic)
                +getStylePrompt(style)
                +methodologyPromptAssembler.buildTitleGuidance(methodology);

        // P3：标题阶段以选题作 query 检索参考（软参考，失败/空命中不阻断）
        Long userId = state.value(INPUT_USER_ID).map(v -> Long.valueOf(v.toString())).orElse(null);
        RagAugmentationService.AugmentedResult augmented = ragAugmentationService.augment(topic, userId);
        if (!augmented.isEmpty()) {
            prompt += augmented.promptBlock();
        }

        //调用 LLM
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        String content = response.getResult().getOutput().getText();

        //解析结果
        List<ArticleState.TitleOption> titleOptions = GsonUtils.fromJson(content, new TypeToken<List<ArticleState.TitleOption>>(){});

        log.info("TitleGeneratorAgent 执行完成, 生成了{}个标题方案", titleOptions.size());

        Map<String, Object> result = new HashMap<>();
        result.put(OUTPUT_TITLE_OPTIONS, titleOptions);
        if (!augmented.isEmpty()) {
            result.put(KEY_RAG_REFERENCES, augmented.references());
        }
        return result;
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
