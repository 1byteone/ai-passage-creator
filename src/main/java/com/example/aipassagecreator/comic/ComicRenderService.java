package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.card.CardImageResolver;
import com.example.aipassagecreator.card.CardRenderPipeline;
import com.example.aipassagecreator.card.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 漫画 HTML → PNG（复用现有 Playwright 安全渲染管线，JS 禁用 + 布局探针） */
@Slf4j
@Service
public class ComicRenderService {

    /** <img src="..."> 的 src 值 — 标准管线禁网，渲染前须把远端图内联为 data URL 才能显示 */
    private static final Pattern IMG_SRC = Pattern.compile(
            "(?is)(<img\\b[^>]*?\\bsrc\\s*=\\s*\")([^\"]*)(\")");

    private final CardRenderPipeline cardRenderPipeline;
    private final CardImageResolver imageResolver;

    public ComicRenderService(CardRenderPipeline cardRenderPipeline, CardImageResolver imageResolver) {
        this.cardRenderPipeline = cardRenderPipeline;
        this.imageResolver = imageResolver;
    }

    public String renderToPngDataUrl(String html, String taskId) {
        if (!cardRenderPipeline.isHealthy()) {
            log.warn("渲染引擎不可用，跳过 PNG: taskId={}", taskId);
            return null;
        }
        String inlinedHtml = inlineImages(html);
        List<PageResult> results = cardRenderPipeline.render(List.of(inlinedHtml), taskId);
        if (results.isEmpty() || results.get(0).getPngBytes() == null) {
            log.warn("PNG 渲染失败: taskId={}", taskId);
            return null;
        }
        byte[] png = results.get(0).getPngBytes();
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    /**
     * 把 HTML 里所有 &lt;img&gt; 的图片 URL 内联为 base64 data URL。
     * 与卡片流程同源（复用 {@link CardImageResolver#toDataUrl}）：下载失败/超限返回 null
     * 时保留原 URL（模板降级为无图），不阻断整页渲染；已是 data: 的 URL 原样返回。
     */
    private String inlineImages(String html) {
        Matcher matcher = IMG_SRC.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group(2);
            String dataUrl = imageResolver.toDataUrl(url);
            if (dataUrl != null) {
                // quoteReplacement 防 data URL 里的 $ / 反斜杠被替换引擎特殊处理
                matcher.appendReplacement(sb,
                        Matcher.quoteReplacement(matcher.group(1) + dataUrl + "\""));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
