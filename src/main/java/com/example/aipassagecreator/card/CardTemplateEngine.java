package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.illustration.IllustrationCharacterStyle;
import com.example.aipassagecreator.card.model.PagePlan;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;

/**
 * 卡片 HTML 渲染引擎。
 * <p>注入 Spring 自动配置的 Thymeleaf {@link TemplateEngine}（由
 * spring-boot-starter-thymeleaf 提供），将分页方案渲染为独立 HTML 列表。</p>
 * <p>模板路径：{@code templates/cards/{style}.html}。风格统一由 {@link CardStyle}
 * 枚举管理（白名单 + 默认值），未知或 null 风格回退 warm，防止模板路径注入。</p>
 */
@Component
public class CardTemplateEngine {

    /** 默认卡片风格 — 与 {@link CardStyle#from} 的回退值一致，命名化默认值便于统一引用 */
    private static final CardStyle DEFAULT_STYLE = CardStyle.WARM;

    private final TemplateEngine templateEngine;
    private final CardImageResolver imageResolver;

    public CardTemplateEngine(TemplateEngine templateEngine, CardImageResolver imageResolver) {
        this.templateEngine = templateEngine;
        this.imageResolver = imageResolver;
    }

    /**
     * 将分页方案渲染为 HTML 列表（每页独立 HTML）。
     * <p>注入变量：{@code title}/{@code content}/{@code contentHtml}/{@code pageNo}/{@code pageType}/
     * {@code imageDataUrl}（该页配图 base64，无图则为空）。</p>
     * <p>此重载以默认子风格 {@code healing} 委托给 {@link #render(List, String, String)}，
     * 供非插画风格或无需指定子风格的调用方使用。</p>
     *
     * @param pages 分页方案
     * @param style 卡片风格名，未知或 null 由 {@link CardStyle#from} 回退默认
     * @return 每页渲染后的 HTML
     */
    public List<String> render(List<PagePlan> pages, String style) {
        return render(pages, style, "healing");
    }

    /**
     * 将分页方案渲染为 HTML 列表（每页独立 HTML）。
     * <p>在 {@link #render(List, String)} 基础上额外注入 {@code characterStyle}（插画子风格，
     * 经 {@link IllustrationCharacterStyle#from} 归一化，未知/null 回退 healing）。
     * 非插画模板不使用该变量，注入无副作用。</p>
     *
     * @param pages 分页方案
     * @param style 卡片风格名，未知或 null 由 {@link CardStyle#from} 回退默认
     * @param characterStyle 插画子风格（healing/cute/doodle/watercolor），非插画模板可传 null
     * @return 每页渲染后的 HTML
     */
    public List<String> render(List<PagePlan> pages, String style, String characterStyle) {
        String template = "cards/" + resolveStyle(style).getName();
        String normalizedCharStyle = IllustrationCharacterStyle.from(characterStyle).getName();
        return pages.stream().map(page -> {
            Context ctx = new Context();
            ctx.setVariable("title", page.getTitle());
            ctx.setVariable("content", page.getContentMd());
            // 优先 flexmark HTML；为空则回退为纯文本段落，保证模板总能渲染出正文
            String html = (page.getContentHtml() != null && !page.getContentHtml().isBlank())
                    ? page.getContentHtml()
                    : (page.getContentMd() != null
                        ? "<p>" + escapeHtml(page.getContentMd()).replace("\n", "<br/>") + "</p>"
                        : "");
            ctx.setVariable("contentHtml", html);
            ctx.setVariable("pageNo", page.getPageNo());
            ctx.setVariable("pageType", page.getPageType());
            // 插画子风格：模板据此切换色板（设计 6.3），归一化后值域为枚举白名单
            ctx.setVariable("characterStyle", normalizedCharStyle);
            // 配图 base64（方案 A：下载内联，避免标准管线禁网导致远程图加载失败）
            ctx.setVariable("imageDataUrl", imageResolver.toDataUrl(page.getImageUrl()));
            return templateEngine.process(template, ctx);
        }).toList();
    }

    /**
     * 解析风格名：命中枚举返回对应风格，未知或 null 回退默认 WARM。
     */
    private CardStyle resolveStyle(String style) {
        return CardStyle.from(style);
    }

    /** HTML 转义（用于 contentHtml 为空时的纯文本回退，防 XSS） */
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
