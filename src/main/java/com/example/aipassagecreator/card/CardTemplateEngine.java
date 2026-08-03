package com.example.aipassagecreator.card;

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

    public CardTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * 将分页方案渲染为 HTML 列表（每页独立 HTML）。
     * <p>单页渲染：直接向模板上下文注入 {@code title}/{@code content}/{@code pageNo} 三个变量，
     * 模板以顶层变量取用（模板不遍历 {@code pages} 列表）。</p>
     *
     * @param pages 分页方案
     * @param style 卡片风格名，未知或 null 由 {@link CardStyle#from} 回退默认
     * @return 每页渲染后的 HTML
     */
    public List<String> render(List<PagePlan> pages, String style) {
        String template = "cards/" + resolveStyle(style).getName();
        return pages.stream().map(page -> {
            Context ctx = new Context();
            ctx.setVariable("title", page.getTitle());
            ctx.setVariable("content", page.getContentMd());
            ctx.setVariable("pageNo", page.getPageNo());
            return templateEngine.process(template, ctx);
        }).toList();
    }

    /**
     * 解析风格名：命中枚举返回对应风格，未知或 null 回退默认 WARM。
     */
    private CardStyle resolveStyle(String style) {
        return CardStyle.from(style);
    }
}
