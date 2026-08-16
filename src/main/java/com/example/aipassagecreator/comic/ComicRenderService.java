package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.card.CardRenderPipeline;
import com.example.aipassagecreator.card.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 漫画 HTML → PNG（复用现有 Playwright 安全渲染管线，JS 禁用 + 布局探针） */
@Slf4j
@Service
public class ComicRenderService {

    private final CardRenderPipeline cardRenderPipeline;

    public ComicRenderService(CardRenderPipeline cardRenderPipeline) {
        this.cardRenderPipeline = cardRenderPipeline;
    }

    public String renderToPngDataUrl(String html, String taskId) {
        if (!cardRenderPipeline.isHealthy()) {
            log.warn("渲染引擎不可用，跳过 PNG: taskId={}", taskId);
            return null;
        }
        List<PageResult> results = cardRenderPipeline.render(List.of(html), taskId);
        if (results.isEmpty() || results.get(0).getPngBytes() == null) {
            log.warn("PNG 渲染失败: taskId={}", taskId);
            return null;
        }
        byte[] png = results.get(0).getPngBytes();
        return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png);
    }
}
