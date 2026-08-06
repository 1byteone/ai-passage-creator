package com.example.aipassagecreator.service;

import cn.hutool.json.JSONUtil;
import com.example.aipassagecreator.agent.ArticleAgentOrchestrator;
import com.example.aipassagecreator.agent.config.AgentConfig;
import com.example.aipassagecreator.enums.ArticlePhaseEnum;
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.enums.SseMessageTypeEnum;
import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ArticleAsyncService {

    @Resource
    private ArticleAgentService articleAgentService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private ArticleService articleService;

    @Resource
    private ArticleAgentOrchestrator articleAgentOrchestrator;

    @Resource
    private AgentConfig agentConfig;

    @Resource
    private ArticleQualityGateService articleQualityGateService;

    @Resource
    private ContentQualityService contentQualityService;

    @Resource
    private RagService ragService;

    /**
     * 异步执行文章生成任务
     *
     * @param taskId 任务 ID
     * @param topic  选题
     * @param style
     */
    @Async("articleExecutor")
    public void executeArticleGeneration(String taskId, String topic, String style) {
        log.info("异步任务开始，taskId={},topic={}",taskId, topic);

        try{
            //更新状态为处理中
            articleService.updateArticleStatus(taskId,ArticleStatusEnum.PROCESSING,null);

            //创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setTopic(topic);
            state.setStyle(style);

            //执行智能体编排，并通过 SSE 推送进度
            articleAgentService.executeArticleGeneration(state, message -> {
                handleAgentMessage(taskId,message, state);
            });

            //保存完整文章到数据库
            articleService.saveArticleContent(taskId,state);

            //更新状态为完成
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.COMPLETED,null);

            // RAG 向量索引
            Article savedArticle = articleService.getByTaskId(taskId);
            if (savedArticle != null) {
                ragService.indexArticleAsync(savedArticle);
            }

            //推送完成消息
            sendSseMessage(taskId, SseMessageTypeEnum.ALL_COMPLETE, Map.of("taskId",taskId));

            //完成SSE连接
            sseEmitterManager.complete(taskId);

            log.info("异步任务完成，taskId={}",taskId);
        }catch (Exception e){
            log.error("异步任务失败，taskId={}",taskId,e);

            //更新状态为失败
            articleService.updateArticleStatus(taskId,ArticleStatusEnum.FAILED,e.getMessage());

            //推送错误消息
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("taskId",taskId,"error",e.getMessage()));

            //完成SSE连接
            sseEmitterManager.complete(taskId);
        }
    }


    /**
     * 阶段1：生成标题方案
     *
     * @param taskId      任务 ID
     * @param topic       选题
     * @param style       文章风格（可为空）
     * @param methodology 方法论模板名称（可为空，回退 default）
     * */
    @Async("articleExecutor")
    public void executePhase1(String taskId, String topic, String style, String methodology){
        boolean orchestratorEnabled = agentConfig.isOrchestratorEnabled();
        log.info("阶段1异步任务开始，taskId={},topic={},style={},methodology={}",taskId, topic, style, methodology);

        try{
            //更新状态和阶段
            articleService.updateArticleStatus(taskId,ArticleStatusEnum.PROCESSING,null);
            articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_GENERATING);

            //创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setTopic(topic);
            state.setStyle(style);
            state.setMethodology(methodology != null ? methodology : "default");
            // executePhase1 时文章可能尚未持久化（测试场景），从 DB 读 characterStyle
            Article phase1Article = articleService.getByTaskId(taskId);
            if (phase1Article != null) {
                state.setCharacterStyle(phase1Article.getCharacterStyle());
            }

            //执行阶段1：生成标题方案
            if(orchestratorEnabled){
                articleAgentOrchestrator.executePhase1_GenerateTitles(state, message -> {
                    handleAgentMessage(taskId,message, state);
                });
            }else {
                articleAgentService.executePhase1_GenerateTitles(state, message -> {
                    handleAgentMessage(taskId,message, state);
                });
            }

            //保存标题方案到数据库
            articleService.saveTitleOptions(taskId,state.getTitleOptions());

            //更新阶段为等待选择标题
            articleService.updatePhase(taskId, ArticlePhaseEnum.TITLE_SELECTING);

            //推送标题方案生成完成消息
            Map<String, Object> data = new HashMap<>();
            data.put("titleOptions",state.getTitleOptions());
            sendSseMessage(taskId, SseMessageTypeEnum.TITLES_GENERATED, data);

            log.info("阶段1异步任务完成，taskId={}",taskId);
        }catch (Exception e){
            log.error("阶段1异步任务失败，taskId={}",taskId,e);

            //更新状态为失败
            articleService.updateArticleStatus(taskId,ArticleStatusEnum.FAILED,e.getMessage());

            //推送错误消息
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message",e.getMessage()));

            //完成SSE连接
            sseEmitterManager.complete(taskId);
        }

    }

    @Async("articleExecutor")
    public void executePhase2(String taskId){
        boolean orchestratorEnabled = agentConfig.isOrchestratorEnabled();
        log.info("阶段2异步任务开始，taskId={}",taskId);

        try{
            //获取文章信息
            Article article = articleService.getByTaskId(taskId);
            if(article == null){
                throw new RuntimeException("文章不存在");
            }

            //创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setStyle(article.getStyle());
            state.setMethodology(article.getMethodology() != null ? article.getMethodology() : "default");
            state.setUserDescription(article.getUserDescription());

            //设置标题
            ArticleState.TitleResult title = new ArticleState.TitleResult();
            title.setMainTitle(article.getMainTitle());
            title.setSubTitle(article.getSubTitle());
            state.setTitle(title);
            state.setCharacterStyle(article.getCharacterStyle());

            //执行阶段2：生成大纲
            if(orchestratorEnabled){
                articleAgentOrchestrator.executePhase2_GenerateOutline(state, message -> {
                    handleAgentMessage(taskId,message, state);
                });
            }else{
                articleAgentService.executePhase2_GenerateOutline(state, message -> {
                    handleAgentMessage(taskId,message, state);
                });
            }

            //保存大纲到数据库
            Article articleToUpdate = articleService.getByTaskId(taskId);
            articleToUpdate.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
            articleService.updateById(articleToUpdate);

            //更新阶段为等待编辑大纲
            articleService.updatePhase(taskId, ArticlePhaseEnum.OUTLINE_EDITING);

            //推送大纲生成完成消息
            Map<String, Object> data = new HashMap<>();
            data.put("outline",state.getOutline().getSections());
            sendSseMessage(taskId, SseMessageTypeEnum.OUTLINE_GENERATED, data);

            log.info("阶段2异步任务完成，taskId={}",taskId);
        }catch (Exception e){
            log.error("阶段2异步任务失败，taskId={}",taskId,e);
            //更新状态为失败
            articleService.updateArticleStatus(taskId,ArticleStatusEnum.FAILED,e.getMessage());

            //推送错误消息
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message",e.getMessage()));

            //完成SSE连接
            sseEmitterManager.complete(taskId);

        }
    }

    /**
     * 阶段3：异步生成正文+配图（用户确认大纲后调用）
     *
     * @param taskId 任务ID
     */
    @Async("articleExecutor")
    public void executePhase3(String taskId) {
        boolean orchestratorEnabled = agentConfig.isOrchestratorEnabled();
        log.info("阶段3异步任务开始, taskId={}", taskId);

        try {
            // 获取文章信息
            Article article = articleService.getByTaskId(taskId);
            if (article == null) {
                throw new RuntimeException("文章不存在");
            }

            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setStyle(article.getStyle());
            state.setMethodology(article.getMethodology() != null ? article.getMethodology() : "default");

            // 从数据库获取允许的配图方式
            List<String> enabledMethods = null;
            if (article.getEnabledImageMethods() != null) {
                enabledMethods = GsonUtils.fromJson(
                        article.getEnabledImageMethods(),
                        new TypeToken<List<String>>(){}
                );
            }
            state.setEnabledImageMethods(enabledMethods);

            // 设置标题
            ArticleState.TitleResult title = new ArticleState.TitleResult();
            title.setMainTitle(article.getMainTitle());
            title.setSubTitle(article.getSubTitle());
            state.setTitle(title);

            // 设置大纲
            List<ArticleState.OutlineSection> outlineSections = GsonUtils.fromJson(
                    article.getOutline(),
                    new TypeToken<List<ArticleState.OutlineSection>>(){}
            );
            ArticleState.OutlineResult outlineResult = new ArticleState.OutlineResult();
            outlineResult.setSections(outlineSections);
            state.setOutline(outlineResult);
            state.setCharacterStyle(article.getCharacterStyle());

            // 执行阶段3：生成正文+配图
            if (orchestratorEnabled) {
                articleAgentOrchestrator.executePhase3_GenerateContent(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            }else {
                articleAgentService.executePhase3_GenerateContent(state, message -> {
                    handleAgentMessage(taskId, message, state);
                });
            }

            // 质量门检测（生成内容后、保存前）
            ArticleQualityGateService.GateResult gate = articleQualityGateService
                    .checkAndDetox(state, taskId, article.getUserId());

            // 保存完整文章到数据库
            articleService.saveArticleContent(taskId, state);

            // 更新状态为已完成（evaluateViral 要求 COMPLETED 状态）
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.COMPLETED, null);

            // RAG 向量索引：文章完成后异步嵌入向量库（失败静默，不影响主流程）
            Article savedArticle = articleService.getByTaskId(taskId);
            if (savedArticle != null) {
                ragService.indexArticleAsync(savedArticle);
            }

            // VIP/管理员专属爆款评分（必须在 COMPLETED 之后）
            BigDecimal viralScore = null;
            if (articleQualityGateService.isVipOrAdmin(article.getUserId())) {
                try {
                    ArticleQuality viral = contentQualityService.evaluateViral(
                            taskId, state.getMethodology(), article.getUserId());
                    viralScore = viral != null ? viral.getViralScore() : null;
                } catch (Exception e) {
                    log.error("爆款评分失败: taskId={}", taskId, e);
                }
            }

            // 推送质量门报告（ALL_COMPLETE 之前）
            Map<String, Object> qualityData = new HashMap<>();
            qualityData.put("taskId", taskId);
            qualityData.put("score", gate.score());
            qualityData.put("passed", gate.passed());
            qualityData.put("detoxed", gate.detoxed());
            qualityData.put("violations", gate.violations());
            if (viralScore != null) {
                qualityData.put("viralScore", viralScore);
            }
            sendSseMessage(taskId, SseMessageTypeEnum.QUALITY_CHECKED, qualityData);

            // 推送完成消息
            sendSseMessage(taskId, SseMessageTypeEnum.ALL_COMPLETE, Map.of("taskId", taskId));

            // 完成 SSE 连接
            sseEmitterManager.complete(taskId);

            log.info("阶段3异步任务完成, taskId={}", taskId);
        } catch (Exception e) {
            log.error("阶段3异步任务失败, taskId={}", taskId, e);

            articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, e.getMessage());
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR, Map.of("message", e.getMessage()));
            sseEmitterManager.complete(taskId);
        }
    }



    /**
     * 处理智能体消息并推送
     */
    private void handleAgentMessage(String taskId, String message, ArticleState state) {
        Map<String, Object> data = buildMessageData(message, state);
        if (data != null) {
            sseEmitterManager.send(taskId, GsonUtils.toJson(data));
        }
    }

    /**
     * 构建消息数据
     */
    private Map<String, Object> buildMessageData(String message, ArticleState state) {
        // 处理流式消息（带冒号分隔符）
        String streamingPrefix2 = SseMessageTypeEnum.AGENT2_STREAMING.getStreamingPrefix();
        String streamingPrefix3 = SseMessageTypeEnum.AGENT3_STREAMING.getStreamingPrefix();
        String imageCompletePrefix = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix();

        if (message.startsWith(streamingPrefix2)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT2_STREAMING,
                    message.substring(streamingPrefix2.length()));
        }

        if (message.startsWith(streamingPrefix3)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT3_STREAMING,
                    message.substring(streamingPrefix3.length()));
        }

        if (message.startsWith(imageCompletePrefix)) {
            String imageJson = message.substring(imageCompletePrefix.length());
            return buildImageCompleteData(imageJson);
        }

        // 处理完成消息（枚举值）
        return buildCompleteMessageData(message, state);
    }

    /**
     * 构建流式输出数据
     */
    private Map<String, Object> buildStreamingData(SseMessageTypeEnum type, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.put("content", content);
        return data;
    }

    /**
     * 构建图片完成数据
     */
    private Map<String, Object> buildImageCompleteData(String imageJson) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", SseMessageTypeEnum.IMAGE_COMPLETE.getValue());
        data.put("image", GsonUtils.fromJson(imageJson, ArticleState.ImageResult.class));
        return data;
    }

    /**
     * 构建完成消息数据
     */
    private Map<String, Object> buildCompleteMessageData(String message, ArticleState state) {
        Map<String, Object> data = new HashMap<>();

        if (SseMessageTypeEnum.AGENT1_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            data.put("title", state.getTitle());
        } else if (SseMessageTypeEnum.AGENT2_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            data.put("outline", state.getOutline().getSections());
        } else if (SseMessageTypeEnum.AGENT3_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
        } else if (SseMessageTypeEnum.AGENT4_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
            data.put("imageRequirements", state.getImageRequirements());
        } else if (SseMessageTypeEnum.AGENT5_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
            data.put("images", state.getImages());
        } else if (SseMessageTypeEnum.MERGE_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.MERGE_COMPLETE.getValue());
            data.put("fullContent", state.getFullContent());
        } else {
            return null;
        }

        return data;
    }

    /**
     * 发送 SSE 消息
     */
    private void sendSseMessage(String taskId, SseMessageTypeEnum type, Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.putAll(additionalData);
        sseEmitterManager.send(taskId, JSONUtil.toJsonStr(data));
    }


}
