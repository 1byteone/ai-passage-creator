package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.PagePlan;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CardStructurePlannerTest {

    private final CardStructurePlanner planner = new CardStructurePlanner();

    @Test
    void plan_createsCoverAndContentPages() {
        String content = "## 第一章\n\n这是第一章内容。\n\n## 第二章\n\n这是第二章内容。";
        List<PagePlan> pages = planner.plan(content, "主标题", "副标题", null);
        assertEquals(3, pages.size()); // 封面 + 2 内容页
        assertEquals("COVER", pages.get(0).getPageType());
        assertEquals("CONTENT", pages.get(1).getPageType());
    }

    @Test
    void plan_emptyContent_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> planner.plan("", "标题", "副标题", null));
    }

    @Test
    void plan_stripsPlaceholders() {
        String content = "正文 {{IMAGE_PLACEHOLDER_1}} 内容";
        List<PagePlan> pages = planner.plan(content, "标题", "副标题", null);
        assertFalse(pages.get(1).getContentMd().contains("PLACEHOLDER"));
    }
}
