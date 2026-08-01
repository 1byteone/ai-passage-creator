package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.PagePlan;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.Set;

/**
 * 卡片 HTML 渲染引擎。
 * <p>注入 Spring 自动配置的 Thymeleaf {@link TemplateEngine}（由
 * spring-boot-starter-thymeleaf 提供），将分页方案渲染为独立 HTML 列表。</p>
 * <p>模板路径：{@code templates/cards/{style}.html}。风格经过白名单校验
 * （warm/minimal/free），未知或 null 风格回退 warm，防止模板路径注入。</p>
 */
@Component
public class CardTemplateEngine {

    /** 支持的卡片风格白名单（对应 templates/cards/{style}.html） */
    private static final Set<String> SUPPORTED_STYLES = Set.of("warm", "minimal", "free");

    private static final String DEFAULT_STYLE = "warm";

    private final TemplateEngine templateEngine;

    public CardTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * 将分页方案渲染为 HTML 列表（每页独立 HTML）。
     *
     * @param pages 分页方案
     * @param style 卡片风格（warm/minimal/free），未知或 null 回退 warm
     * @return 每页渲染后的 HTML
     */
    public List<String> render(List<PagePlan> pages, String style) {
        String template = "cards/" + resolveStyle(style);
        return pages.stream().map(page -> {
            Context ctx = new Context();
            ctx.setVariable("pages", List.of(page));
            return templateEngine.process(template, ctx);
        }).toList();
    }

    /**
     * 解析风格名：命中白名单则原样返回，否则回退默认 warm。
     */
    private String resolveStyle(String style) {
        if (style != null && SUPPORTED_STYLES.contains(style)) {
            return style;
        }
        return DEFAULT_STYLE;
    }
}
