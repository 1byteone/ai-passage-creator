package com.example.aipassagecreator.card;

import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.ArticleService;
import com.example.aipassagecreator.service.QuotaService;
import com.example.aipassagecreator.service.UserService;
import com.example.aipassagecreator.utils.GsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 卡片异步生成服务。
 * <p>由 {@code cardExecutor} 线程池执行，串行走完
 * {@link CardService#generate}，并通过 SSE 推送 card_progress / card_complete / card_error 事件。
 * 任何异常均吞入 SSE error 并 complete，不向上抛出。</p>
 */
@Slf4j
@Service
public class CardAsyncService {

    private final CardService cardService;
    private final ArticleService articleService;
    private final SseEmitterManager sseEmitterManager;
    private final QuotaService quotaService;
    private final UserService userService;

    public CardAsyncService(CardService cardService, ArticleService articleService,
                            SseEmitterManager sseEmitterManager,
                            QuotaService quotaService, UserService userService) {
        this.cardService = cardService;
        this.articleService = articleService;
        this.sseEmitterManager = sseEmitterManager;
        this.quotaService = quotaService;
        this.userService = userService;
    }

    @Async("cardExecutor")
    public void generateCards(String taskId, String cardStyle, String methodologyName, Long loginUserId) {
        try {
            Article article = articleService.getByTaskId(taskId);
            if (article == null) {
                log.error("卡片生成失败，文章不存在: taskId={}", taskId);
                sseEmitterManager.send(taskId, GsonUtils.toJson(Map.of("type", "card_error", "error", "文章不存在")));
                sseEmitterManager.complete(taskId);
                return;
            }
            String fullContent = article.getFullContent() != null
                    ? article.getFullContent() : article.getContent();
            sseEmitterManager.send(taskId, GsonUtils.toJson(Map.of("type", "card_progress", "message", "开始分页...")));

            cardService.generate(fullContent, article.getMainTitle(),
                    article.getSubTitle(), article.getCoverImage(), article.getImages(),
                    cardStyle, taskId, methodologyName);

            sseEmitterManager.send(taskId, GsonUtils.toJson(Map.of("type", "card_complete", "taskId", taskId)));
            sseEmitterManager.complete(taskId);
            log.info("卡片生成完成: taskId={}", taskId);
        } catch (Exception e) {
            log.error("卡片生成失败: taskId={}", taskId, e);
            // 配额补偿：异步执行失败退还已扣配额（admin/VIP 内部跳过），避免扣了配额但用户未获得产出
            // 退还需查库，包一层 try-catch 保证配额退还异常不阻断 SSE error 推送
            try {
                User user = userService.getById(loginUserId);
                if (user != null) {
                    quotaService.refundQuota(user);
                    log.info("卡片生成失败，已退还配额: taskId={}, userId={}", taskId, loginUserId);
                } else {
                    log.warn("退还配额跳过，用户不存在: taskId={}, userId={}", taskId, loginUserId);
                }
            } catch (Exception quotaEx) {
                log.error("退还配额失败: taskId={}, userId={}", taskId, loginUserId, quotaEx);
            }
            String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
            sseEmitterManager.send(taskId, GsonUtils.toJson(Map.of("type", "card_error", "error", msg)));
            sseEmitterManager.complete(taskId);
        }
    }
}
