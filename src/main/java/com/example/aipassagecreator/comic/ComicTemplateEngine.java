package com.example.aipassagecreator.comic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 漫画手帐 HTML 渲染引擎（Thymeleaf，自建模板）。
 * 模板路径 templates/comic/{style}/episode.html|monthly.html，画风经 ComicStyle 白名单防注入。
 * 所有 LLM 注入值用 th:text 自动转义，杜绝 XSS。
 */
@Slf4j
@Component
public class ComicTemplateEngine {

    /** 模板里的样式表 <link> 元素 — 渲染后由引擎内联为 <style>，见 {@link #inlineCss} */
    private static final Pattern STYLESHEET_LINK = Pattern.compile(
            "(?is)<link\\b[^>]*\\brel\\s*=\\s*[\"']stylesheet[\"'][^>]*/?>");
    /** style 缺省时回退默认画风，避免 null 解引用 */
    private static final ComicStyle FALLBACK_STYLE = ComicStyle.POWDER;

    private final TemplateEngine templateEngine;

    public ComicTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String renderEpisode(ComicStyle style, String title, List<Map<String, Object>> panels,
                                List<String> imageUrls, List<Map<String, Object>> textBlocks,
                                String coverSubtitle) {
        ComicStyle resolvedStyle = style != null ? style : FALLBACK_STYLE;
        Context ctx = new Context();
        ctx.setVariable("title", title);
        ctx.setVariable("coverSubtitle", coverSubtitle);
        ctx.setVariable("panels", panels);
        ctx.setVariable("imageUrls", imageUrls);
        ctx.setVariable("textBlocks", textBlocks);
        ctx.setVariable("styleCss", resolvedStyle.getCssFile());
        String html = templateEngine.process(resolvedStyle.getTemplatePrefix() + "/episode", ctx);
        return inlineCss(html, resolvedStyle);
    }

    public String renderMonthly(ComicStyle style, String title, List<Map<String, Object>> episodes) {
        ComicStyle resolvedStyle = style != null ? style : FALLBACK_STYLE;
        Context ctx = new Context();
        ctx.setVariable("title", title);
        ctx.setVariable("episodes", episodes);
        ctx.setVariable("styleCss", resolvedStyle.getCssFile());
        String html = templateEngine.process(resolvedStyle.getTemplatePrefix() + "/monthly", ctx);
        return inlineCss(html, resolvedStyle);
    }

    /**
     * 把渲染后 HTML 里的样式表 <link> 就地替换为 <style> + 该画风 CSS 内容。
     * 标准渲染管线（CardRenderPipeline）拦截所有网络请求且无静态资源处理器，
     * 外链 CSS 在 srcdoc 预览与 PNG 渲染中都加载不到；内联后 HTML 自包含。
     * CSS 缺失时保留原 <link>（仍是合法 HTML），不阻断渲染。
     */
    private String inlineCss(String html, ComicStyle style) {
        String css = loadCss(style);
        if (css == null) {
            return html;
        }
        Matcher matcher = STYLESHEET_LINK.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            // quoteReplacement 防 CSS 内容里的 $ / 反斜杠被替换引擎特殊处理
            matcher.appendReplacement(sb, Matcher.quoteReplacement("<style>" + css + "</style>"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 读取画风 CSS classpath 资源；缺失/异常返回 null（调用方降级保留原 <link>） */
    private String loadCss(ComicStyle style) {
        String path = "templates/" + style.getCssFile();
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.warn("漫画画风 CSS 不存在，跳过内联: path={}", path);
                return null;
            }
            // try-with-resources: 流用完即关，避免重复渲染泄漏 classpath 流
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("漫画画风 CSS 读取失败，跳过内联: path={}, err={}", path, e.getMessage());
            return null;
        }
    }
}
