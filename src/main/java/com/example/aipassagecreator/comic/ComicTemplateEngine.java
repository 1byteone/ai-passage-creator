package com.example.aipassagecreator.comic;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Map;

/**
 * 漫画手帐 HTML 渲染引擎（Thymeleaf，自建模板）。
 * 模板路径 templates/comic/{style}/episode.html|monthly.html，画风经 ComicStyle 白名单防注入。
 * 所有 LLM 注入值用 th:text 自动转义，杜绝 XSS。
 */
@Component
public class ComicTemplateEngine {

    private final TemplateEngine templateEngine;

    public ComicTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String renderEpisode(ComicStyle style, String title, List<Map<String, Object>> panels,
                                List<String> imageUrls, List<Map<String, Object>> textBlocks,
                                String coverSubtitle) {
        Context ctx = new Context();
        ctx.setVariable("title", title);
        ctx.setVariable("coverSubtitle", coverSubtitle);
        ctx.setVariable("panels", panels);
        ctx.setVariable("imageUrls", imageUrls);
        ctx.setVariable("textBlocks", textBlocks);
        ctx.setVariable("styleCss", style.getCssFile());
        return templateEngine.process(style.getTemplatePrefix() + "/episode", ctx);
    }

    public String renderMonthly(ComicStyle style, String title, List<Map<String, Object>> episodes) {
        Context ctx = new Context();
        ctx.setVariable("title", title);
        ctx.setVariable("episodes", episodes);
        ctx.setVariable("styleCss", style.getCssFile());
        return templateEngine.process(style.getTemplatePrefix() + "/monthly", ctx);
    }
}
