package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.card.CardRenderPipeline;
import com.example.aipassagecreator.card.model.PageResult;
import com.example.aipassagecreator.handwriting.config.HandwritingRenderConfig;
import com.example.aipassagecreator.handwriting.model.HandwritingPage;
import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import com.example.aipassagecreator.service.CosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 手写编辑器业务编排。
 * 职责：分页 → 渲染 HTML → Playwright 截图 → COS 上传。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandwritingService {

    private final HandwritingStructurePlanner planner;
    private final HandwritingRenderer renderer;
    private final CardRenderPipeline pipeline;
    private final HandwritingRenderConfig config;
    private final CosService cosService;

    /**
     * 预览：渲染单页手写效果并上传到 COS，返回预签名 URL。
     * 同步执行，不扣配额。
     */
    public String preview(HandwritingRequest request) {
        List<HandwritingPage> pages = planner.plan(request.content(), null);
        HandwritingPage firstPage = pages.isEmpty()
                ? new HandwritingPage(1, null, request.content())
                : pages.get(0);

        HandwritingRequest pageReq = new HandwritingRequest(
                firstPage.contentMd(),
                request.fontName(), request.paperType(),
                request.effectiveParams(), request.paperImageUrl());
        String html = renderer.renderToHtml(pageReq);

        List<PageResult> results = pipeline.renderWithJs(
                List.of(html), "preview-" + System.nanoTime(), config);

        if (results.isEmpty() || results.get(0).getPngBytes() == null) {
            throw new IllegalStateException("手写预览渲染失败");
        }

        byte[] png = results.get(0).getPngBytes();
        String key = "handwriting/preview/" + System.nanoTime() + ".png";
        cosService.uploadToKey(png, "image/png", key);
        return cosService.generatePresignedUrl(key);
    }

    /**
     * 导出：全量分页 → 渲染 → 逐页上传 → 返回图片 URL 列表。
     */
    public List<String> export(HandwritingRequest request, String taskId, String title) {
        List<HandwritingPage> pages = planner.plan(request.content(), title);

        List<String> htmls = pages.stream().map(page -> {
            HandwritingRequest pageReq = new HandwritingRequest(
                    page.contentMd(),
                    request.fontName(), request.paperType(),
                    request.effectiveParams(), request.paperImageUrl());
            return renderer.renderToHtml(pageReq);
        }).toList();

        List<PageResult> results = pipeline.renderWithJs(htmls, taskId, config);

        return results.stream().map(r -> {
            if (r.getPngBytes() == null) return null;
            String key = "handwriting/" + taskId + "/export/" + r.getPageNo() + ".png";
            cosService.uploadToKey(r.getPngBytes(), "image/png", key);
            return cosService.generatePresignedUrl(key);
        }).filter(url -> url != null).toList();
    }
}
