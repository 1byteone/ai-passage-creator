package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.ComplianceReport;
import com.example.aipassagecreator.card.model.PagePlan;
import com.example.aipassagecreator.card.model.PageResult;
import com.example.aipassagecreator.service.CosService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 图文卡片生成编排服务。
 * <p>职责：分页 → 文本合规预检 → HTML 渲染 → Playwright 截图 → COS 上传 → 持久化。
 * <ul>
 *   <li>{@link #preview}：同步渲染前 2 页并上传，返回可预览的图片 URL（上传失败跳过）；</li>
 *   <li>{@link #generate}：全量渲染，先硬门禁文本合规（ERROR 级不通过即抛异常阻断），
 *       再逐页上传并落库 {@code article_card}。</li>
 * </ul></p>
 */
@Slf4j
@Service
public class CardService {

    private final CardStructurePlanner planner;
    private final CardTemplateEngine templateEngine;
    private final CardRenderPipeline renderPipeline;
    private final CardComplianceChecker complianceChecker;
    private final CardPageMapper cardPageMapper;
    private final CosService cosService;

    public CardService(CardStructurePlanner planner, CardTemplateEngine templateEngine,
                       CardRenderPipeline renderPipeline, CardComplianceChecker complianceChecker,
                       CardPageMapper cardPageMapper, CosService cosService) {
        this.planner = planner;
        this.templateEngine = templateEngine;
        this.renderPipeline = renderPipeline;
        this.complianceChecker = complianceChecker;
        this.cardPageMapper = cardPageMapper;
        this.cosService = cosService;
    }

    /**
     * 预览：分页 + 渲染前 2 页 + 上传 + 返回 URL。
     * 同步，预期 2-4s。单页上传失败时跳过（不阻断整体），保证预览可部分返回。
     */
    public List<String> preview(String fullContent, String mainTitle,
                                String subTitle, String coverImage,
                                String cardStyle, String taskId) {
        List<PagePlan> pages = planner.plan(fullContent, mainTitle, subTitle, coverImage);
        List<PagePlan> previewPages = pages.subList(0, Math.min(2, pages.size()));
        List<String> htmls = templateEngine.render(previewPages, cardStyle);
        List<PageResult> results = renderPipeline.render(htmls, taskId);
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            PageResult r = results.get(i);
            if (r.getPngBytes() != null && r.isLayoutPassed()) {
                String key = cardCosKey(taskId, previewPages.get(i).getPageNo());
                cosService.uploadToKey(r.getPngBytes(), "image/png", key);
                String presigned = cosService.generatePresignedUrl(key);
                if (presigned != null) urls.add(presigned);
            }
        }
        return urls;
    }

    /**
     * 全量生成：分页 + 文本合规预检 + 渲染 + 上传 + 持久化。
     * 文本合规为硬门禁：任一 ERROR 级规则失败即抛 {@link IllegalStateException}（含规则明细），
     * 不落库。幂等：先删该 taskId 的旧卡再插入。
     */
    public List<CardPage> generate(String fullContent, String mainTitle,
                                   String subTitle, String coverImage,
                                   String cardStyle, String taskId,
                                   String methodologyName) {
        // 1. 分页
        List<PagePlan> pages = planner.plan(fullContent, mainTitle, subTitle, coverImage);

        // 2. 文本合规（硬门禁）
        ComplianceReport textReport = complianceChecker.textCheck(pages, mainTitle, methodologyName);
        if (!textReport.isPassed()) {
            String detail = textReport.getRules().stream()
                    .filter(r -> "ERROR".equals(r.getLevel()) && !r.isPassed())
                    .map(r -> r.getRuleName() + ": " + r.getMessage())
                    .reduce((a, b) -> a + "; " + b).orElse("");
            log.warn("卡片文本合规未通过，阻断生成: taskId={}, detail={}", taskId, detail);
            throw new IllegalStateException("合规检查未通过: " + detail);
        }

        // 3. 渲染
        List<String> htmls = templateEngine.render(pages, cardStyle);
        List<PageResult> results = renderPipeline.render(htmls, taskId);

        // 4. 上传 + 持久化（幂等：先删旧卡）。用确定性 COS key + 预签名 URL
        cardPageMapper.deleteByQuery(QueryWrapper.create().eq("task_id", taskId));
        List<CardPage> cardPages = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            PageResult r = results.get(i);
            String key = cardCosKey(taskId, i + 1);
            if (r.getErrorMessage() != null) {
                CardPage failed = buildCardPage(taskId, i + 1, cardStyle, null, key, 0, 0,
                        "FAILED", r.getErrorMessage());
                cardPageMapper.insert(failed);
                cardPages.add(failed);
                continue;
            }
            String uploadResult = cosService.uploadToKey(r.getPngBytes(), "image/png", key);
            if (uploadResult == null) {
                // COS 上传失败 → 标记 FAILED，不生成 URL
                CardPage failed = buildCardPage(taskId, i + 1, cardStyle, null, key,
                        r.getPngBytes() != null ? r.getPngBytes().length : 0,
                        r.getRenderMs(), "FAILED", "COS 上传失败");
                cardPageMapper.insert(failed);
                cardPages.add(failed);
                continue;
            }
            String presignedUrl = cosService.generatePresignedUrl(key);
            CardPage cp = buildCardPage(taskId, i + 1, cardStyle,
                    presignedUrl, key,
                    r.getPngBytes() != null ? r.getPngBytes().length : 0,
                    r.getRenderMs(),
                    presignedUrl != null ? "COMPLETED" : "FAILED",
                    presignedUrl == null ? "COS 上传失败" : null);
            cardPageMapper.insert(cp);
            cardPages.add(cp);
        }
        log.info("卡片生成完成: taskId={}, pages={}, completed={}",
                taskId, cardPages.size(),
                cardPages.stream().filter(c -> "COMPLETED".equals(c.getStatus())).count());
        return cardPages;
    }

    /** 确定性 COS key：cards/{taskId}/{pageNo}.png，可重复覆盖 */
    private static String cardCosKey(String taskId, int pageNo) {
        return "cards/" + taskId + "/" + pageNo + ".png";
    }

    private CardPage buildCardPage(String taskId, int pageNo, String style,
                                   String imageUrl, String imageKey, int bytes,
                                   int renderMs, String status, String errorMessage) {
        return CardPage.builder()
                .taskId(taskId).pageNo(pageNo).pageType(pageNo == 1 ? "COVER" : "CONTENT")
                .style(style).imageUrl(imageUrl).imageKey(imageKey)
                .width(1080).height(1920).bytes(bytes).status(status)
                .errorMessage(errorMessage).renderMs(renderMs)
                .createTime(LocalDateTime.now()).updateTime(LocalDateTime.now())
                .build();
    }
}
