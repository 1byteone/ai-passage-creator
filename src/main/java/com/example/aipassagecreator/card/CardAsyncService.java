package com.example.aipassagecreator.card;

import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.service.ArticleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

    public CardAsyncService(CardService cardService, ArticleService articleService,
                            SseEmitterManager sseEmitterManager) {
        this.cardService = cardService;
        this.articleService = articleService;
        this.sseEmitterManager = sseEmitterManager;
    }

    @Async("cardExecutor")
    public void generateCards(String taskId, String cardStyle, String methodologyName, Long loginUserId) {
        try {
            Article article = articleService.getByTaskId(taskId);
            if (article == null) {
                log.error("卡片生成失败，文章不存在: taskId={}", taskId);
                return;
            }
            String fullContent = article.getFullContent() != null
                    ? article.getFullContent() : article.getContent();
            sseEmitterManager.send(taskId, "{\"type\":\"card_progress\",\"message\":\"开始分页...\"}");

            cardService.generate(fullContent, article.getMainTitle(),
                    article.getSubTitle(), article.getCoverImage(),
                    cardStyle, taskId, methodologyName);

            sseEmitterManager.send(taskId, "{\"type\":\"card_complete\",\"taskId\":\"" + taskId + "\"}");
            sseEmitterManager.complete(taskId);
            log.info("卡片生成完成: taskId={}", taskId);
        } catch (Exception e) {
            log.error("卡片生成失败: taskId={}", taskId, e);
            sseEmitterManager.send(taskId, "{\"type\":\"card_error\",\"error\":\"" +
                    e.getMessage().replace("\"", "\\\"") + "\"}");
            sseEmitterManager.complete(taskId);
        }
    }
}
