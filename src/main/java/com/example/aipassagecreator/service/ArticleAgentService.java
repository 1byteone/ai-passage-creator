package com.example.aipassagecreator.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.example.aipassagecreator.annotation.AgentExecution;
import com.example.aipassagecreator.constant.PromptConstant;
import com.example.aipassagecreator.enums.ArticleStyleEnum;
import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.enums.SseMessageTypeEnum;
import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.methodology.MethodologyPromptAssembler;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.dto.image.ImageRequest;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
public class ArticleAgentService {

    @Resource
    private DashScopeChatModel chatModel;

    @Resource
    private ImageServiceStrategy imageServiceStrategy;

    @Resource
    private CosService cosService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private MethodologyPromptAssembler methodologyPromptAssembler;

    /**
     * 获取当前类的代理对象
     * 用于解决 Spring AOP 同类方法调用代理失效问题
     */
    private ArticleAgentService getProxy() {
        try {
            return (ArticleAgentService) AopContext.currentProxy();
        } catch (IllegalStateException e) {
            // 如果获取代理失败，返回 this（降级处理）
            log.warn("获取 AOP 代理对象失败，使用原始对象: {}", e.getMessage());
            return this;
        }
    }

    /**
     * 执行完整的文章生成流程
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    @Deprecated
    public void executeArticleGeneration(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体1：生成标题
            log.info("智能体1：开始生成标题, taskId={}", state.getTaskId());
            agent1GenerateTitle(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());

            // 智能体2：生成大纲（流式输出）
            log.info("智能体2：开始生成大纲, taskId={}", state.getTaskId());
            agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());

            // 智能体3：生成正文（流式输出）
            log.info("智能体3：开始生成正文, taskId={}", state.getTaskId());
            agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());

            // 智能体4：分析配图需求
            log.info("智能体4：开始分析配图需求, taskId={}", state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());

            // 智能体5：生成配图
            log.info("智能体5：开始生成配图, taskId={}", state.getTaskId());
            agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());

            // 图文合成：将配图插入正文
            log.info("开始图文合成, taskId={}", state.getTaskId());
            mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());

            log.info("文章生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("文章生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("文章生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 智能体1：生成标题
     *
     * @param state 文章状态
     */
    @Deprecated
    private void agent1GenerateTitle(ArticleState state) {
        // 使用单个标题 Prompt（返回对象格式）
        String prompt = PromptConstant.AGENT1_SINGLE_TITLE_PROMPT
                .replace("{topic}", state.getTopic())
                +getStylePrompt(state.getStyle());  //添加风格Prompt

        String content = callLlm(prompt);
        ArticleState.TitleResult titleResult = parseJsonResponse(content, ArticleState.TitleResult.class,"标题");
        state.setTitle(titleResult);
        log.info("智能体1：标题生成成功，mainTitle={}", titleResult.getMainTitle());
    }

    /**
     * 阶段1：生成标题方案（3-5个）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase1_GenerateTitles(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体1：生成标题方案
            log.info("阶段1：开始生成标题方案, taskId={}", state.getTaskId());
            //通过代理调用，使AOP生效
            getProxy().agent1GenerateTitleOptions(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());
            log.info("阶段1：标题方案生成完成, taskId={}, optionsCount={}",
                    state.getTaskId(), state.getTitleOptions().size());
        } catch (Exception e) {
            log.error("阶段1：标题方案生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("标题方案生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段2：生成大纲（用户选择标题后）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase2_GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体2：生成大纲（流式输出）
            log.info("阶段2：开始生成大纲, taskId={}", state.getTaskId());
            getProxy().agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());
            log.info("阶段2：大纲生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("阶段2：大纲生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("大纲生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 阶段3：生成正文+配图（用户确认大纲后）
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executePhase3_GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        try {
            //获取代理对象
            ArticleAgentService proxy = getProxy();

            // 智能体3：生成正文（流式输出）
            log.info("阶段3：开始生成正文, taskId={}", state.getTaskId());
            proxy.agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());

            // 智能体4：分析配图需求
            log.info("阶段3：开始分析配图需求, taskId={}", state.getTaskId());
            proxy.agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());

            // 智能体5：生成配图
            log.info("阶段3：开始生成配图, taskId={}", state.getTaskId());
            proxy.agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());

            // 图文合成：将配图插入正文
            log.info("阶段3：开始图文合成, taskId={}", state.getTaskId());
            proxy.mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());
            sseEmitterManager.sendHeartbeat(state.getTaskId());

            log.info("阶段3：正文生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("阶段3：正文生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("正文生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 智能体1：生成标题方案（3-5个）
     */
    @AgentExecution(value = "agent1_generate_titles",description = "生成标题方案")
    public void agent1GenerateTitleOptions(ArticleState state) {
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT
                .replace("{topic}", state.getTopic())
                + getStylePrompt(state.getStyle())
                + methodologyPromptAssembler.buildTitleGuidance(state.getMethodology());

        String content = callLlm(prompt);
        List<ArticleState.TitleOption> titleOptions = parseJsonListResponse(
                content,
                new TypeToken<List<ArticleState.TitleOption>>(){},
                "标题方案"
        );
        state.setTitleOptions(titleOptions);
        log.info("智能体1：标题方案生成成功, optionsCount={}", titleOptions.size());
    }


    /**
     * 智能体2：生成大纲
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    @AgentExecution(value = "agent2_generate_outline",description = "生成大纲")
    public void agent2GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                +getStylePrompt(state.getStyle())  //添加风格Prompt
                +methodologyPromptAssembler.buildContentGuidance(state.getMethodology());

        String content = callLlmWithStreaming(prompt, streamHandler,SseMessageTypeEnum.AGENT2_STREAMING);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class,"大纲");
        state.setOutline(outlineResult);
        log.info("智能体2：大纲生成成功，sections={}",outlineResult.getSections().size());

    }


    /**
     * 智能体3：生成正文
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    @AgentExecution(value = "agent3_generate_content",description = "生成正文")
    public void agent3GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        String outlineText = GsonUtils.toJson(state.getOutline().getSections());
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{outline}", outlineText)
                +getStylePrompt(state.getStyle())  //添加风格Prompt
                +methodologyPromptAssembler.buildContentGuidance(state.getMethodology());


        String content = callLlmWithStreaming(prompt, streamHandler,SseMessageTypeEnum.AGENT3_STREAMING);
        state.setContent(content);
        log.info("智能体3：正文生成成功，length={}", content.length());
    }

    /**
     * 智能体4：分析配图需求
     */
    @AgentExecution(value = "agent4_analyze_image_requirements",description = "分析配图需求")
    public void agent4AnalyzeImageRequirements(ArticleState state) {
        //构建可用配图方式说明
        String availableMethods = buildAvailableMethodsDescription(state.getEnabledImageMethods());

        String prompt = PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{content}", state.getContent())
                .replace("{availableMethods}", availableMethods);

        String content = callLlm(prompt);

        ArticleState.Agent4Result agent4Result = parseJsonResponse(content, ArticleState.Agent4Result.class,"配图需求");

        //更新正文为包含占位符的版本
        state.setContent(agent4Result.getContentWithPlaceholders());
        state.setImageRequirements(agent4Result.getImageRequirements());
        log.info("智能体4：分析配图需求成功，count={},已在正文中插入占位符", agent4Result.getImageRequirements().size());

       /* List<ArticleState.ImageRequirement> imageRequirements = parseJsonListResponse(
                content, new TypeToken<List<ArticleState.ImageRequirement>>(){},"配图需求");
        state.setImageRequirements(imageRequirements);
        log.info("智能体4：分析配图需求成功，count={}", imageRequirements.size());*/
    }

    /**
     * 智能体5：生成配图(串行执行)
     */
    @AgentExecution(value = "agent5_generate_images",description = "生成配图")
    public void agent5GenerateImages(ArticleState state, Consumer<String> streamHandler) {
        List<ArticleState.ImageResult> imageResults = new ArrayList<>();

        for (ArticleState.ImageRequirement requirement : state.getImageRequirements()) {
            String imageSource = requirement.getImageSource();
            log.info("智能体5：开始检索配图，position={},imageSource={},keywords={}", requirement.getPosition(),imageSource, requirement.getKeywords());

            //构建图片请求对象
            ImageRequest request = ImageRequest.builder()
                    .keywords(requirement.getKeywords())
                    .prompt(requirement.getPrompt())
                    .position(requirement.getPosition())
                    .type(requirement.getType())
                    .build();

            //使用策略模式获取图片并上传到 COS
            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImageAndUpload(imageSource, request);

            String cosUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            //创建配图结果（URL 已经是 cos地址)
            ArticleState.ImageResult imageResult = buildImageResult(requirement, cosUrl, method);
            imageResults.add(imageResult);

            //推送单张配图成功
            String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix()+GsonUtils.toJson(imageResult);
            streamHandler.accept(imageCompleteMessage);

            log.info("智能体5：生成配图成功，position={},method={},cosUrl={}", requirement.getPosition(),method.getValue(),cosUrl);

           /* //调用图片检索服务
            String imageUrl = imageSearchService.searchImage(requirement.getKeywords());

            //降级策略
            ImageMethodEnum method = imageSearchService.getMethod();
            if(imageUrl == null){
                imageUrl = imageSearchService.getFallbackImage(requirement.getPosition());
                method = ImageMethodEnum.PICSUM;
                log.warn("智能体5：图片检索失败，使用降级策略，position={}", requirement.getPosition());
            }

            //使用图片直接URL(MVP阶段不上传到 COS ,简化流程)
            String finalImageUrl = cosService.useDirectUrl(imageUrl);

            //创建配图结果
            ArticleState.ImageResult imageResult = buildImageResult(requirement, finalImageUrl, method);
            imageResults.add(imageResult);

            //推送单张配图成功
            String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix()+GsonUtils.toJson(imageResult);
            streamHandler.accept(imageCompleteMessage);

            log.info("智能体5：生成配图成功，position={},url={}", requirement.getPosition(), finalImageUrl);*/
        }
        state.setImages(imageResults);
        log.info("智能体5：生成配图完成，count={}", imageResults.size());
    }

    /**
     * 图文合成：将配图插入正文对应位置
     */
    @AgentExecution(value = "agent6_merge_content",description = "图文合成")
    public void mergeImagesIntoContent(ArticleState state) {
        String content = state.getContent();
        List<ArticleState.ImageResult> images = state.getImages();

        if(images == null || images.isEmpty()){
            state.setFullContent( content);
            return;
        }

        String fullContent = content;

        //遍历所有配图，根据占位符替换为实际图片
        for (ArticleState.ImageResult image : images) {
            String placeholderId = image.getPlaceholderId();
            if(placeholderId != null && !placeholderId.isEmpty()){
                String imageMarkdown = "!["+image.getDescription()+"]("+image.getUrl()+")";
                fullContent = fullContent.replace(placeholderId, imageMarkdown);
            }
        }


        state.setFullContent(fullContent);
        log.info("图文合成完成，fullContentLength={}",fullContent.length());
    }

    /**
     * AI 修改大纲
     *
     * @param mainTitle        主标题
     * @param subTitle         副标题
     * @param currentOutline   当前大纲
     * @param modifySuggestion 用户修改建议
     * @return 修改后的大纲
     */
    @AgentExecution(value = "ai_modify_outline",description = "AI 修改大纲")
    public List<ArticleState.OutlineSection> aiModifyOutline(String mainTitle, String subTitle,
                                                             List<ArticleState.OutlineSection> currentOutline,
                                                             String modifySuggestion) {
        String currentOutlineJson = GsonUtils.toJson(currentOutline);

        String prompt = PromptConstant.AI_MODIFY_OUTLINE_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{currentOutline}", currentOutlineJson)
                .replace("{modifySuggestion}", modifySuggestion);

        String content = callLlm(prompt);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "修改后的大纲");

        log.info("AI修改大纲成功, sectionsCount={}", outlineResult.getSections().size());
        return outlineResult.getSections();
    }

    /**
     * 构建可用配图方式说明
     */
    private String buildAvailableMethodsDescription(List<String> enabledMethods) {
        // 如果为空或 null，表示支持所有方式
        if (enabledMethods == null || enabledMethods.isEmpty()) {
            return getAllMethodsDescription();
        }

        // 只描述允许的方式
        StringBuilder sb = new StringBuilder();
        for (String method : enabledMethods) {
            ImageMethodEnum methodEnum = ImageMethodEnum.getByValue(method);
            if (methodEnum != null && !methodEnum.isFallback()) {
                sb.append("   - ").append(methodEnum.getValue())
                        .append(": ").append(getMethodUsageDescription(methodEnum))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取配图方式的使用说明
     */
    private String getMethodUsageDescription(ImageMethodEnum method) {
        return switch (method) {
            case PEXELS -> "适合真实场景、产品照片、人物照片、自然风景等写实图片";
            case NANO_BANANA -> "适合创意插画、信息图表、需要文字渲染、抽象概念、艺术风格等 AI 生成图片";
            case MERMAID -> "适合流程图、架构图、时序图、关系图、甘特图等结构化图表";
            case ICONIFY -> "适合图标、符号、小型装饰性图标（如：箭头、勾选、星星、心形等）";
            case EMOJI_PACK -> "适合表情包、搞笑图片、轻松幽默的配图";
            case SVG_DIAGRAM -> "适合概念示意图、思维导图样式、逻辑关系展示（不涉及精确数据）";
            default -> method.getDescription();
        };
    }

    /**
     * 获取所有配图方式的完整描述
     */
    private String getAllMethodsDescription() {
        return """
               - PEXELS: 适合真实场景、产品照片、人物照片、自然风景等写实图片
               - NANO_BANANA: 适合创意插画、信息图表、需要文字渲染、抽象概念、艺术风格等 AI 生成图片
               - MERMAID: 适合流程图、架构图、时序图、关系图、甘特图等结构化图表
               - ICONIFY: 适合图标、符号、小型装饰性图标（如：箭头、勾选、星星、心形等）
               - EMOJI_PACK: 适合表情包、搞笑图片、轻松幽默的配图
               - SVG_DIAGRAM: 适合概念示意图、思维导图样式、逻辑关系展示（不涉及精确数据）
               """;
    }


    // region 辅助方法

    /**
     * 调用 LLM（非流式）
     */
    private String callLlm(String prompt) {
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        return response.getResult().getOutput().getText();
    }

    /**
     * 调用 LLM（流式输出）
     */
    private String callLlmWithStreaming(String prompt, Consumer<String> streamHandler, SseMessageTypeEnum messageType) {
        StringBuilder contentBuilder = new StringBuilder();

        Flux<ChatResponse> streamResponse = chatModel.stream(new Prompt(new UserMessage(prompt)));

        streamResponse
                .doOnNext(response -> {
                    String chunk = response.getResult().getOutput().getText();
                    if (chunk != null && !chunk.isEmpty()) {
                        contentBuilder.append(chunk);
                        streamHandler.accept(messageType.getStreamingPrefix() + chunk);
                    }
                })
                .doOnError(error -> log.error("LLM 流式调用失败, messageType={}", messageType, error))
                .blockLast();

        return contentBuilder.toString();
    }

    /**
     * 解析 JSON 响应
     */
    private <T> T parseJsonResponse(String content, Class<T> clazz, String name) {
        try {
            return GsonUtils.fromJson(content, clazz);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 解析 JSON 列表响应
     */
    private <T> T parseJsonListResponse(String content, TypeToken<T> typeToken, String name) {
        try {
            return GsonUtils.fromJson(content, typeToken);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 构建配图结果
     */
    private ArticleState.ImageResult buildImageResult(ArticleState.ImageRequirement requirement,
                                                      String imageUrl,
                                                      ImageMethodEnum method) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(requirement.getPosition());
        imageResult.setUrl(imageUrl);
        imageResult.setMethod(method.getValue());
        imageResult.setKeywords(requirement.getKeywords());
        imageResult.setSectionTitle(requirement.getSectionTitle());
        imageResult.setDescription(requirement.getType());
        imageResult.setPlaceholderId(requirement.getPlaceholderId()); //记录占位符ID
        return imageResult;
    }

    /**
     * 在章节标题后插入对应图片
     */
    private void insertImageAfterSection(StringBuilder fullContent,
                                         List<ArticleState.ImageResult> images,
                                         String sectionTitle) {
        for (ArticleState.ImageResult image : images) {
            if (image.getPosition() > 1 &&
                    image.getSectionTitle() != null &&
                    sectionTitle.contains(image.getSectionTitle().trim())) {
                fullContent.append("\n![").append(image.getDescription())
                        .append("](").append(image.getUrl()).append(")\n");
                break;
            }
        }
    }

    /**
     * 获取风格提示
     */
    private String getStylePrompt(String  style){
        if(style == null|| style.isEmpty()){
            return "";
        }

        ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
        if(styleEnum == null){
            return "";
        }

        return switch (styleEnum){
            case TECH -> PromptConstant.STYLE_TECH_PROMPT;
            case EMOTIONAL ->PromptConstant.STYLE_EMOTIONAL_PROMPT;
            case EDUCATIONAL -> PromptConstant.STYLE_EDUCATIONAL_PROMPT;
            case HUMOROUS -> PromptConstant.STYLE_HUMOROUS_PROMPT;
        };
    }


// endregion

}
