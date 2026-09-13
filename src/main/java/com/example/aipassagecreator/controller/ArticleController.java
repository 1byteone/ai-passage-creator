package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.DeleteRequest;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.enums.ArticleStyleEnum;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.model.dto.article.*;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.AgentExecutionStats;
import com.example.aipassagecreator.model.vo.ArticleVO;
import com.example.aipassagecreator.service.AgentLogService;
import com.example.aipassagecreator.service.ArticleAsyncService;
import com.example.aipassagecreator.service.ArticleService;
import com.example.aipassagecreator.service.ArticleRewriteService;
import jakarta.validation.Valid;
import com.example.aipassagecreator.service.ContentQualityService;
import com.example.aipassagecreator.service.UserService;
import com.example.aipassagecreator.skill.SkillExecution;
import com.example.aipassagecreator.skill.SkillExecutionService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Resource
    private ArticleService articleService;

    @Resource
    private ArticleAsyncService articleAsyncService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private UserService userService;

    @Resource
    private ContentQualityService contentQualityService;

    @Resource
    private ArticleRewriteService articleRewriteService;

    @Resource
    private com.example.aipassagecreator.service.ExportService exportService;

    @Resource
    private com.example.aipassagecreator.methodology.MethodologyRegistry methodologyRegistry;

    @Resource
    private com.example.aipassagecreator.methodology.MethodologyRefiner methodologyRefiner;

    @Resource
    private SkillExecutionService skillExecutionService;

    /**
     * 创建文章任务
     * @param request
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/create")
    @Operation(summary = "创建文章任务")
    public BaseResponse<String> createArticle(@Valid @RequestBody ArticleCreateRequest request, HttpServletRequest httpServletRequest){
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTopic() == null || request.getTopic().trim().isEmpty()
                , ErrorCode.PARAMS_ERROR, "选题不能为空");
        ThrowUtils.throwIf(!ArticleStyleEnum.isValid(request.getStyle()), ErrorCode.PARAMS_ERROR, "无效的文章风格");

        // 校验方法论：未知值回退 "default"
        String methodology = request.getMethodology();
        if (methodology == null || methodology.isBlank() || !methodologyRegistry.exists(methodology)) {
            methodology = "default";
        }

        User  loginUser = userService.getLoginUser(httpServletRequest);

        // 检查并消耗配额 + 创建文章任务（在同一事务中）
        String taskId = articleService.createArticleTaskWithQuotaCheck(
                request.getTopic(),
                request.getStyle(),
                methodology,
                request.getEnabledImageMethods(),
                request.getCharacterStyle(),
                loginUser
        );

        // 异步执行阶段1：生成标题方案
        articleAsyncService.executePhase1(taskId, request.getTopic(), request.getStyle(), methodology);

        return ResultUtils.success(taskId);
    }

    /**
     * 确认标题并输入补充描述
     */
    @PostMapping("/confirm-title")
    @Operation(summary = "确认标题并输入补充描述")
    public BaseResponse<Void> confirmTitle(@Valid @RequestBody ArticleConfirmTitleRequest request, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getSelectedMainTitle() == null || request.getSelectedMainTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "主标题不能为空");
        ThrowUtils.throwIf(request.getSelectedSubTitle() == null || request.getSelectedSubTitle().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "副标题不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 确认标题
        articleService.confirmTitle(
                request.getTaskId(),
                request.getSelectedMainTitle(),
                request.getSelectedSubTitle(),
                request.getUserDescription(),
                loginUser
        );

        // 异步执行阶段2：生成大纲
        articleAsyncService.executePhase2(request.getTaskId());

        return ResultUtils.success(null);
    }

    /**
     * 确认大纲
     */
    @PostMapping("/confirm-outline")
    @Operation(summary = "确认大纲")
    public BaseResponse<Void> confirmOutline(@Valid @RequestBody ArticleConfirmOutlineRequest request,
                                             HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getOutline() == null || request.getOutline().isEmpty(),
                ErrorCode.PARAMS_ERROR, "大纲不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 确认大纲
        articleService.confirmOutline(
                request.getTaskId(),
                request.getOutline(),
                loginUser
        );

        // 异步执行阶段3：生成正文+配图
        articleAsyncService.executePhase3(request.getTaskId());

        return ResultUtils.success(null);
    }

    /**
     * AI 修改大纲
     */
    @PostMapping("/ai-modify-outline")
    @Operation(summary = "AI 修改大纲")
    public BaseResponse<List<ArticleState.OutlineSection>> aiModifyOutline(
            @Valid @RequestBody ArticleAiModifyOutlineRequest request,
            HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getTaskId() == null || request.getTaskId().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        ThrowUtils.throwIf(request.getModifySuggestion() == null || request.getModifySuggestion().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "修改建议不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // AI 修改大纲
        List<ArticleState.OutlineSection> modifiedOutline = articleService.aiModifyOutline(
                request.getTaskId(),
                request.getModifySuggestion(),
                loginUser
        );

        return ResultUtils.success(modifiedOutline);
    }

    @Resource
    private AgentLogService agentLogService;

    /**
     * 获取任务执行日志
     */
    @GetMapping("/execution-logs/{taskId}")
    @Operation(summary = "获取任务执行日志")
    @AuthCheck(mustRole = "user")
    public BaseResponse<AgentExecutionStats> getExecutionLogs(@PathVariable String taskId,
                                                              HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        // 归属校验：防止 IDOR，仅文章作者或管理员可查看执行日志
        var article = articleService.getByTaskId(taskId);
        if (article == null) {
            throw new com.example.aipassagecreator.exception.BusinessException(
                    ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        if (!article.getUserId().equals(loginUser.getId())
                && !com.example.aipassagecreator.constant.UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new com.example.aipassagecreator.exception.BusinessException(
                    ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
        }

        AgentExecutionStats stats = agentLogService.getExecutionStats(taskId);
        return ResultUtils.success(stats);
    }



    /**
     * SSE 进度推送
     */
    @GetMapping("/progress/{taskId}")
    @Operation(summary = "获取文章生成进度（SSE）")
    public SseEmitter getProgress(@PathVariable String taskId, HttpServletRequest request){
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        //校验权限（内部会检查任务是否存在以及用户是否有权限访问）
        User loginUser = userService.getLoginUser(request);
        articleService.getArticleDetail(taskId, loginUser);

        //创建 SSE Emitter
        SseEmitter sseEmitter = sseEmitterManager.createEmitter(taskId);

        log.info("SSE 连接已建立，taskId={}",taskId);
        return sseEmitter;
    }


    /**
     * 获取文章详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取文章详情")
    @AuthCheck(mustRole = "user")
    public BaseResponse<ArticleVO> getArticle(@PathVariable String taskId,
                                              HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);
        ArticleVO articleVO = articleService.getArticleDetail(taskId, loginUser);

        return ResultUtils.success(articleVO);
    }

    /**
     * 分页查询文章列表
     */
    @PostMapping("/list")
    @Operation(summary = "分页查询文章列表")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Page<ArticleVO>> listArticle(@Valid @RequestBody ArticleQueryRequest request,
                                                     HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        Page<ArticleVO> articleVOPage = articleService.listArticleByPage(request, loginUser);

        return ResultUtils.success(articleVOPage);
    }

    /**
     * 删除文章
     */
    @PostMapping("/delete")
    @Operation(summary = "删除文章")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Boolean> deleteArticle(@Valid @RequestBody DeleteRequest deleteRequest,
                                               HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null,
                ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(httpServletRequest);
        boolean result = articleService.deleteArticle(deleteRequest.getId(), loginUser);

        return ResultUtils.success(result);
    }

    /**
     * 对文章进行多维质量评分
     */
    @PostMapping("/evaluate-quality")
    @Operation(summary = "对文章进行多维质量评分")
    @AuthCheck(mustRole = "user")
    @RateLimit(limit = 5, window = 60, key = "quality_evaluate")
    public BaseResponse<?> evaluateQuality(@RequestBody DeleteRequest request,
                                           HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null || request.getId() == null,
                ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(httpServletRequest);
        // 修复: request.getId() 是 DB 主键 Long，先用主键查 article，再获取真实 taskId
        var article = articleService.getById(request.getId());
        if (article == null || !article.getUserId().equals(loginUser.getId())) {
            throw new com.example.aipassagecreator.exception.BusinessException(
                    ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
        }

        try {
            var result = contentQualityService.evaluate(article.getTaskId());
            return ResultUtils.success(result);
        } catch (IllegalArgumentException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
    }

    /**
     * 爆款维度评测
     */
    @PostMapping("/evaluate-viral")
    @Operation(summary = "爆款维度评测")
    @AuthCheck(mustRole = "user")
    @RateLimit(limit = 5, window = 60, key = "viral_evaluate")
    public BaseResponse<?> evaluateViral(@Valid @RequestBody ArticleEvaluateViralRequest request,
                                         HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null || request.getTaskId() == null
                || request.getTaskId().trim().isEmpty(), ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 归属校验：防止 IDOR
        var article = articleService.getByTaskId(request.getTaskId());
        if (article == null || !article.getUserId().equals(loginUser.getId())) {
            throw new com.example.aipassagecreator.exception.BusinessException(
                    ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
        }

        try {
            ArticleQuality result = contentQualityService.evaluateViral(
                    request.getTaskId(), request.getMethodologyName(), loginUser.getId());
            return ResultUtils.success(result);
        } catch (IllegalArgumentException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
    }

    /**
     * 爆款反哺闭环：低分维度定向改写
     */
    @PostMapping("/refine")
    @Operation(summary = "爆款反哺（低分维度定向改写）")
    @AuthCheck(mustRole = "user")
    @RateLimit(limit = 3, window = 60, key = "viral_refine")
    public BaseResponse<?> refine(@Valid @RequestBody ArticleEvaluateViralRequest request,
                                  HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null || request.getTaskId() == null
                || request.getTaskId().trim().isEmpty(), ErrorCode.PARAMS_ERROR, "任务ID不能为空");

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 归属校验：防止 IDOR
        var article = articleService.getByTaskId(request.getTaskId());
        if (article == null || !article.getUserId().equals(loginUser.getId())) {
            throw new com.example.aipassagecreator.exception.BusinessException(
                    ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
        }

        try {
            var result = methodologyRefiner.refine(
                    request.getTaskId(), request.getMethodologyName(), loginUser.getId());
            return ResultUtils.success(result);
        } catch (IllegalArgumentException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
    }

    /**
     * AI 改写文章（多轮迭代）
     */
    @PostMapping("/rewrite")
    @Operation(summary = "AI 改写文章")
    @AuthCheck(mustRole = "user")
    @RateLimit(limit = 3, window = 60, key = "article_rewrite")
    public BaseResponse<?> rewriteArticle(@Valid @RequestBody ArticleAiModifyOutlineRequest request,
                                           HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null || request.getTaskId() == null,
                ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(httpServletRequest);
        String taskId = request.getTaskId();

        // 归属校验
        var article = articleService.getByTaskId(taskId);
        if (article == null || !article.getUserId().equals(loginUser.getId())) {
            throw new com.example.aipassagecreator.exception.BusinessException(
                    ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
        }

        try {
            var version = articleRewriteService.rewrite(
                    taskId, request.getModifySuggestion(), 3, loginUser.getId());
            return ResultUtils.success(version);
        } catch (IllegalArgumentException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
    }

    /**
     * 获取文章版本历史
     */
    @GetMapping("/versions/{taskId}")
    @Operation(summary = "获取文章版本历史")
    @AuthCheck(mustRole = "user")
    public BaseResponse<?> getVersionHistory(@PathVariable String taskId,
                                              HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        var article = articleService.getByTaskId(taskId);
        if (article == null || !article.getUserId().equals(loginUser.getId())) {
            throw new com.example.aipassagecreator.exception.BusinessException(
                    ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
        }
        return ResultUtils.success(articleRewriteService.getVersionHistory(taskId));
    }

    /**
     * 回退文章到指定版本
     */
    @PostMapping("/revert")
    @Operation(summary = "回退文章到指定版本")
    @AuthCheck(mustRole = "user")
    public BaseResponse<?> revertArticle(@Valid @RequestBody ArticleRevertRequest request,
                                          HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null || request.getTaskId() == null || request.getVersionNo() == null,
                ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(httpServletRequest);
        String taskId = request.getTaskId();

        // 归属校验：防止 IDOR
        var article = articleService.getByTaskId(taskId);
        if (article == null || !article.getUserId().equals(loginUser.getId())) {
            throw new com.example.aipassagecreator.exception.BusinessException(
                    ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
        }

        try {
            var version = articleRewriteService.revertTo(taskId, request.getVersionNo(), loginUser.getId());
            return ResultUtils.success(version);
        } catch (IllegalArgumentException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
    }

    /**
     * 导出文章为 PDF/DOCX/PPTX 文件下载
     */
    @GetMapping("/export/{taskId}")
    @Operation(summary = "导出文章为 PDF/DOCX/PPTX")
    @AuthCheck(mustRole = "user")
    public org.springframework.http.ResponseEntity<byte[]> exportArticle(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "pdf") String format,
            HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);

        // 归属校验：防止 IDOR
        var article = articleService.getByTaskId(taskId);
        if (article == null || !article.getUserId().equals(loginUser.getId())) {
            throw new com.example.aipassagecreator.exception.BusinessException(
                    ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
        }

        com.example.aipassagecreator.service.ExportService.Format exportFormat;
        try {
            exportFormat = com.example.aipassagecreator.service.ExportService.Format.valueOf(
                    format.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.example.aipassagecreator.exception.BusinessException(
                    ErrorCode.PARAMS_ERROR, "不支持的导出格式: " + format + "，支持 PDF/DOCX/PPTX");
        }

        byte[] content = exportService.exportArticle(taskId, exportFormat);
        String filename = article.getMainTitle() != null ? article.getMainTitle() : article.getTopic();
        String ext = exportFormat.name().toLowerCase();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                .filename(filename + "." + ext).build());
        headers.setContentType(
                org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);

        return new org.springframework.http.ResponseEntity<>(content, headers,
                org.springframework.http.HttpStatus.OK);
    }

    /**
     * 利用已完成的文章内容一键执行 Skill（表达质量优化 / 文章转稿 / 种草文案等）。
     * <p>
     * 文章归属校验 + COMPLETED 状态校验后走公共派发，进度走既有 /skill/{executionId}/progress SSE。
     */
    @PostMapping("/{taskId}/skill/{skillName}")
    @Operation(summary = "一键执行 Skill（完成后调用）")
    public BaseResponse<?> executeArticleSkill(
            @PathVariable String taskId,
            @PathVariable String skillName,
            HttpServletRequest httpServletRequest) {

        User loginUser = userService.getLoginUser(httpServletRequest);

        // 归属校验：防止 IDOR
        var article = articleService.getByTaskId(taskId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }
        if (!article.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
        }
        if (!ArticleStatusEnum.COMPLETED.getValue().equals(article.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文章未完成，暂不支持执行 Skill");
        }

        // 按 skillName 构建输入
        Map<String, Object> inputs = buildArticleSkillInputs(skillName, article);

        SkillExecution execution = skillExecutionService.dispatchAndExecute(
                skillName, inputs, loginUser);

        Map<String, Object> result = new HashMap<>();
        result.put("skillExecutionId", execution.getExecutionId());
        result.put("skillName", skillName);
        result.put("status", "RUNNING");
        result.put("progressUrl", "/skill/" + execution.getExecutionId() + "/progress");
        return ResultUtils.success(result);
    }

    /**
     * 按 Skill 名称构建一键执行输入。
     * <p>ai-detox/article-to-x → articleContent；seeding-copy → productInfo/platform/tone。</p>
     */
    private Map<String, Object> buildArticleSkillInputs(String skillName, Article article) {
        String content = article.getFullContent();
        if (content == null || content.isBlank()) {
            content = article.getContent();
        }

        return switch (skillName) {
            case "ai-detox" -> {
                Map<String, Object> m = new HashMap<>();
                m.put("articleContent", content != null ? content : "");
                m.put("intensity", "medium");
                yield m;
            }
            case "article-to-x" -> {
                Map<String, Object> m = new HashMap<>();
                m.put("articleContent", content != null ? content : "");
                yield m;
            }
            case "seeding-copy" -> {
                String title = article.getMainTitle() != null ? article.getMainTitle()
                        : article.getTopic() != null ? article.getTopic() : "";
                Map<String, Object> m = new HashMap<>();
                m.put("productInfo", title);
                m.put("platform", "xiaohongshu");
                m.put("tone", "种草推荐");
                yield m;
            }
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "不支持的 Skill: " + skillName + "，支持: ai-detox / article-to-x / seeding-copy");
        };
    }

}
