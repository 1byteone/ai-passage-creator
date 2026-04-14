package com.example.aipassagecreator.agent.agents;


import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.aipassagecreator.constant.PromptConstant;
import com.example.aipassagecreator.agent.context.StreamHandlerContext;
import com.example.aipassagecreator.enums.ArticleStyleEnum;
import com.example.aipassagecreator.enums.SseMessageTypeEnum;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 大纲生成 Agent
 * 根据标题生成文章大纲
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutlineGeneratorAgent implements NodeAction {

    private final DashScopeChatModel chatModel;

    public static final String INPUT_MAIN_TITLE = "mainTitle";
    public static final String INPUT_SUB_TITLE = "subTitle";
    public static final String INPUT_USER_DESCRIPTION = "userDescription";
    public static final String INPUT_STYLE = "style";
    public static final String OUTPUT_OUTLINE = "outline";


    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE)
                .map(Object::toString)
                .orElseThrow(()-> new IllegalArgumentException("缺少主题参数"));

        String subTitle = state.value(INPUT_SUB_TITLE)
                .map(Object::toString)
                .orElse("");

        String userDescription = state.value(INPUT_USER_DESCRIPTION)
                .map(Object::toString)
                .orElse(null);

        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);

        log.info("OutlineGeneratorAgent 启动执行, mainTitle={}, subTitle={}, userDescription={}, style={}", 
                mainTitle, subTitle, userDescription, style);

        //构建用户描述部分
        String descriptionSection="";
        if (userDescription != null && !userDescription.isEmpty()) {
            descriptionSection = PromptConstant.AGENT2_DESCRIPTION_SECTION
                    .replace("{userDescription}", userDescription);
        }

        //构建prompt
        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{descriptionSection}", descriptionSection)
                +getStylePrompt(style);

       //获取流式处理器
        Consumer<String > streamHandler = StreamHandlerContext.get();

        //调用LLM(流式输出)
        String content = callLlmWithStreaming(prompt, streamHandler);

        //检查内容是否为空
        if (content == null || content.trim().isEmpty()) {
            log.error("OutlineGeneratorAgent: LLM返回内容为空");
            throw new RuntimeException("LLM返回内容为空，请检查提示词或模型配置");
        }

        log.debug("OutlineGeneratorAgent: LLM返回内容长度={}, 前500字符={}", 
                content.length(), 
                content.length() > 500 ? content.substring(0, 500) : content);

        //解析结果
        ArticleState.OutlineResult outlineResult = null;
        try {
            outlineResult = GsonUtils.fromJson(content, new TypeToken<ArticleState.OutlineResult>(){});
        } catch (Exception e) {
            log.warn("OutlineGeneratorAgent: JSON解析失败, 尝试修复... content前200字符={}", 
                    content.length() > 200 ? content.substring(0, 200) : content);
            
            // 尝试修复 JSON
            String fixedContent = tryFixJson(content);
            if (fixedContent != null && !fixedContent.equals(content)) {
                log.info("OutlineGeneratorAgent: JSON修复成功, 原始长度={}, 修复后长度={}", 
                        content.length(), fixedContent.length());
                try {
                    outlineResult = GsonUtils.fromJson(fixedContent, new TypeToken<ArticleState.OutlineResult>(){});
                    if (outlineResult != null) {
                        log.info("OutlineGeneratorAgent: 使用修复后的JSON解析成功");
                    }
                } catch (Exception e2) {
                    log.error("OutlineGeneratorAgent: 修复后的JSON仍然解析失败", e2);
                }
            }
            
            // 如果还是失败，抛出原始错误
            if (outlineResult == null) {
                log.error("OutlineGeneratorAgent: JSON解析失败, content前200字符={}", 
                        content.length() > 200 ? content.substring(0, 200) : content, e);
                throw new RuntimeException("大纲JSON解析失败: " + e.getMessage() + ", 原始内容: " + 
                        (content.length() > 500 ? content.substring(0, 500) : content), e);
            }
        }

        //检查解析结果
        if (outlineResult == null) {
            log.error("OutlineGeneratorAgent: JSON解析结果为null, content前500字符={}", 
                    content.length() > 500 ? content.substring(0, 500) : content);
            throw new RuntimeException("大纲解析结果为null，LLM返回格式可能不正确");
        }

        if (outlineResult.getSections() == null || outlineResult.getSections().isEmpty()) {
            log.warn("OutlineGeneratorAgent: 大纲章节列表为空, content前500字符={}", 
                    content.length() > 500 ? content.substring(0, 500) : content);
        }

        log.info("OutlineGeneratorAgent 执行完成: 生成了{}个章节", 
                outlineResult.getSections() != null ? outlineResult.getSections().size() : 0);

        Map<String, Object> result = Map.of(OUTPUT_OUTLINE, outlineResult);
        log.info("OutlineGeneratorAgent 返回结果: key={}, value类型={}", 
                OUTPUT_OUTLINE, 
                outlineResult != null ? outlineResult.getClass().getName() : "null");
        return result;
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
                           streamHandler.accept(SseMessageTypeEnum.AGENT2_STREAMING.getStreamingPrefix()+ chunk);
                       }
                   }
                })
                .doOnError(error -> log.error("LLM 流式输出错误: {}", error.getMessage()))
                .blockLast();

        return contentBuilder.toString();
    }

    /**
     * 尝试修复不完整的 JSON
     */
    private String tryFixJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        
        String fixed = json.trim();
        
        // 1. 检查是否以 { 开头
        if (!fixed.startsWith("{")) {
            log.warn("JSON不以{开头，无法修复");
            return null;
        }
        
        // 2. 如果已经以 } 结尾，说明可能是其他问题，不修复
        if (fixed.endsWith("}")) {
            log.warn("JSON以}结尾，但解析失败，可能是格式问题而非截断");
            return null;
        }
        
        log.info("开始修复JSON, 原始长度={}, 最后100字符={}", 
                fixed.length(), 
                fixed.length() > 100 ? fixed.substring(fixed.length() - 100) : fixed);
        
        // 3. 尝试补全缺失的闭合符号
        // 计算未闭合的大括号和方括号数量
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < fixed.length(); i++) {
            char c = fixed.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '{') openBraces++;
                else if (c == '}') openBraces--;
                else if (c == '[') openBrackets++;
                else if (c == ']') openBrackets--;
            }
        }
        
        // 4. 补全缺失的符号
        StringBuilder sb = new StringBuilder(fixed);
        
        // 如果当前在字符串中，先闭合字符串
        if (inString) {
            sb.append("\"");
            log.info("JSON修复: 检测到未闭合的字符串，添加闭合引号");
        }
        
        // 先补全方括号
        while (openBrackets > 0) {
            sb.append("]");
            openBrackets--;
            log.info("JSON修复: 补全一个]");
        }
        
        // 再补全大括号
        while (openBraces > 0) {
            sb.append("}");
            openBraces--;
            log.info("JSON修复: 补全一个}}");
        }
        
        String result = sb.toString();
        log.info("JSON修复: 原始长度={}, 修复后长度={}, 补全了{}个]和{}个}}", 
                fixed.length(), result.length(), 
                (result.length() - fixed.length()) - openBraces, openBraces);
        
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
