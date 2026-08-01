package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.PagePlan;
import com.example.aipassagecreator.service.CosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CardService 编排集成测试。
 * <p>CosService 以 {@link MockitoBean} 替换（避免真实 COS 调用）；
 * CardRenderPipeline 无 Playwright 时优雅降级，故不依赖真实截图。
 * 覆盖：空内容预览抛 IllegalArgumentException（planner 门禁）；Task-3 模板修复后
 * render() 产出非空 content。</p>
 */
@SpringBootTest
class CardServiceIntegrationTest {

    @Autowired
    private CardService cardService;

    @Autowired
    private CardTemplateEngine templateEngine;

    @MockitoBean
    private CosService cosService;

    @Test
    void preview_emptyContent_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> cardService.preview("", "标题", "副标题", null, "warm", "test"));
    }

    @Test
    void render_producesNonEmptyContent() {
        // Task-3 修复验证：模板以顶层 content 变量取用正文，渲染结果必须包含正文而非空壳
        PagePlan page = PagePlan.builder()
                .pageNo(1).pageType("COVER")
                .title("测试标题")
                .contentMd("这是卡片正文内容")
                .contentHtml("<p>这是卡片正文内容</p>")
                .build();
        List<String> htmls = templateEngine.render(List.of(page), "warm");
        assertEquals(1, htmls.size());
        assertNotNull(htmls.get(0));
        assertFalse(htmls.get(0).isBlank(), "渲染 HTML 不应为空");
        assertTrue(htmls.get(0).contains("这是卡片正文内容"), "渲染 HTML 必须包含正文内容");
        assertTrue(htmls.get(0).contains("测试标题"), "渲染 HTML 必须包含标题");
        assertTrue(htmls.get(0).contains("1"), "渲染 HTML 必须包含页码");
    }
}
