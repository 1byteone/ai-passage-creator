package com.example.aipassagecreator.card;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.card.model.CardGenerateRequest;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.methodology.MethodologyRegistry;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.ArticleService;
import com.example.aipassagecreator.service.QuotaService;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 图文卡片生成端点。
 * <p>三个端点均先做 归属校验 + 状态校验（仅 COMPLETED 文章），
 * 再执行预览 / 异步生成（先扣配额）/ 查询。</p>
 */
@RestController
@RequestMapping("/article/cards")
@Slf4j
public class CardController {

    @Resource
    private CardService cardService;
    @Resource
    private CardAsyncService cardAsyncService;
    @Resource
    private ArticleService articleService;
    @Resource
    private UserService userService;
    @Resource
    private QuotaService quotaService;
    @Resource
    private MethodologyRegistry methodologyRegistry;
    @Resource
    private CardPageMapper cardPageMapper;
    @Resource
    private com.example.aipassagecreator.service.CosService cosService;

    /**
     * 卡片预览（同步渲染前 2 页并上传）。
     * 仅校验不扣配额：预览失败不应消耗用户配额。
     */
    @PostMapping("/preview")
    @Operation(summary = "卡片预览（前 2 页）")
    @AuthCheck(mustRole = "user")
    @RateLimit(limit = 5, window = 60, key = "card_preview")
    public BaseResponse<?> preview(@Valid @RequestBody CardGenerateRequest request,
                                              HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null || request.getTaskId() == null
                || request.getTaskId().trim().isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpServletRequest);
        Article article = validateAndGetArticle(request.getTaskId(), loginUser);

        String effectiveMethodology = Optional.ofNullable(request.getMethodologyName())
                .filter(s -> !s.isBlank())
                .orElse(Optional.ofNullable(article.getMethodology())
                        .filter(s -> !s.isBlank())
                        .orElse("default"));
        String cardStyle = resolveCardStyle(request.getCardStyle(), effectiveMethodology);
        try {
            List<String> urls = cardService.preview(
                    article.getFullContent() != null ? article.getFullContent() : article.getContent(),
                    article.getMainTitle(), article.getSubTitle(),
                    article.getCoverImage(), article.getImages(), cardStyle, request.getTaskId());
            return ResultUtils.success(urls);
        } catch (IllegalArgumentException e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getMessage());
        }
    }

    /**
     * 卡片生成（异步）。
     * 先扣配额再派发异步任务；配额不足直接返回，不派发。
     */
    @PostMapping("/generate")
    @Operation(summary = "卡片生成（异步）")
    @AuthCheck(mustRole = "user")
    @RateLimit(limit = 3, window = 60, key = "card_generate")
    public BaseResponse<?> generate(@Valid @RequestBody CardGenerateRequest request,
                                                      HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(request == null || request.getTaskId() == null
                || request.getTaskId().trim().isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpServletRequest);
        Article article = validateAndGetArticle(request.getTaskId(), loginUser);

        // 方法论预校验：未知方法论直接拒绝，避免扣配额后才异步失败（配额无谓消耗）
        if (request.getMethodologyName() != null && !request.getMethodologyName().isBlank()
                && !methodologyRegistry.exists(request.getMethodologyName())) {
            throw new IllegalArgumentException("未知方法论: " + request.getMethodologyName());
        }

        // 方法论追溯：优先请求值 → 文章创建时方法论(article.methodology) → 默认 "default"
        String effectiveMethodology = Optional.ofNullable(request.getMethodologyName())
                .filter(s -> !s.isBlank())
                .orElse(Optional.ofNullable(article.getMethodology())
                        .filter(s -> !s.isBlank())
                        .orElse("default"));

        // 配额扣减（原子）：不足则抛 BusinessException，直接返回不派发
        try {
            quotaService.checkAndConsumeQuota(loginUser, "卡片生成配额不足，请升级会员");
        } catch (BusinessException e) {
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, e.getMessage());
        }

        String cardStyle = resolveCardStyle(request.getCardStyle(), effectiveMethodology);
        try {
            cardAsyncService.generateCards(request.getTaskId(), cardStyle,
                    effectiveMethodology,
                    loginUser.getId());
        } catch (Exception e) {
            // 异步派发失败（如线程池拒绝）→ 退还配额，避免扣了配额但任务从未执行
            log.error("卡片生成异步派发失败，退还配额: taskId={}", request.getTaskId(), e);
            quotaService.refundQuota(loginUser);
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "卡片生成任务派发失败，请稍后重试");
        }

        return ResultUtils.success(Map.of("taskId", request.getTaskId(),
                "progressUrl", "/article/cards/" + request.getTaskId()));
    }

    /**
     * 查询已生成卡片列表（按页码升序）。
     * 返回前对 COMPLETED 卡片用 imageKey 实时生成短时效预签名 URL（10min），
     * 避免存储的 1h 签名过期后图片破裂。
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "查询已生成卡片列表")
    @AuthCheck(mustRole = "user")
    public BaseResponse<List<CardPage>> getCards(@PathVariable String taskId,
                                                 HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(httpServletRequest);
        validateAndGetArticle(taskId, loginUser);

        List<CardPage> pages = cardPageMapper.selectListByQuery(
                QueryWrapper.create().eq("task_id", taskId).orderBy("page_no", true));
        // 实时刷新预签名 URL（10min 短时效）
        pages.forEach(cp -> {
            if ("COMPLETED".equals(cp.getStatus()) && cp.getImageKey() != null) {
                String freshUrl = cosService.generatePresignedUrl(cp.getImageKey(), java.time.Duration.ofMinutes(10));
                if (freshUrl != null) cp.setImageUrl(freshUrl);
            }
        });
        return ResultUtils.success(pages);
    }

    /**
     * 归属 + 状态校验：文章必须存在、属于当前用户（或管理员）、且状态为 COMPLETED。
     * 校验失败抛 {@link BusinessException}（由全局异常处理器转统一响应）。
     */
    private Article validateAndGetArticle(String taskId, User loginUser) {
        Article article = articleService.getByTaskId(taskId);
        if (article == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }
        if (!article.getUserId().equals(loginUser.getId())
                && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
        }
        if (!ArticleStatusEnum.COMPLETED.getValue().equals(article.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "仅已完成文章可生成卡片");
        }
        return article;
    }

    /**
     * 解析卡片风格：请求参数优先；否则从方法论 platform.cardStyle 读取；均失败回退 "warm"。
     */
    private String resolveCardStyle(String requestCardStyle, String methodologyName) {
        if (requestCardStyle != null && !requestCardStyle.isBlank()) {
            return CardStyle.from(requestCardStyle).getName();
        }
        try {
            var def = methodologyRegistry.get(
                    methodologyName != null ? methodologyName : "default");
            if (def.getPlatform() != null && def.getPlatform().getCardStyle() != null) {
                return CardStyle.from(def.getPlatform().getCardStyle()).getName();
            }
        } catch (IllegalArgumentException e) {
            log.warn("方法论不存在，卡片风格回退默认 warm: {}", e.getMessage());
        }
        return CardStyle.WARM.getName();
    }
}
