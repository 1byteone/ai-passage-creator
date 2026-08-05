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

    @Test
    void plan_withImages_coverGetsCoverImage() {
        String content = "## 第一章\n\n这是第一章内容。";
        List<CardStructurePlanner.CardImageRef> images = List.of(
                img(1, "https://cos.example.com/cover.png"),
                img(2, "https://cos.example.com/p2.png"));
        List<PagePlan> pages = planner.plan(content, "主标题", "副标题", null, images);

        assertEquals("COVER", pages.get(0).getPageType());
        assertEquals("https://cos.example.com/cover.png", pages.get(0).getImageUrl(), "封面应取 position=1 的图");
    }

    @Test
    void plan_withImages_contentPageGetsPositionImage() {
        String content = "## 第一章\n\n这是第一章内容。";
        List<CardStructurePlanner.CardImageRef> images = List.of(
                img(1, "https://cos.example.com/cover.png"),
                img(2, "https://cos.example.com/p2.png"));
        List<PagePlan> pages = planner.plan(content, "主标题", "副标题", null, images);

        // 封面=position1，第一内容页应对应 position=2
        assertEquals("https://cos.example.com/p2.png", pages.get(1).getImageUrl());
    }

    @Test
    void plan_explicitCoverImage_takesPrecedenceOverImages() {
        String content = "## 第一章\n\n内容。";
        List<CardStructurePlanner.CardImageRef> images = List.of(img(1, "https://img1.png"));
        List<PagePlan> pages = planner.plan(content, "主标题", "副标题",
                "https://explicit-cover.png", images);
        assertEquals("https://explicit-cover.png", pages.get(0).getImageUrl(), "显式 coverImage 应优先");
    }

    @Test
    void plan_emptyImages_coverImageNull() {
        String content = "## 第一章\n\n内容。";
        List<PagePlan> pages = planner.plan(content, "主标题", "副标题", null, List.of());
        assertNull(pages.get(0).getImageUrl(), "无配图时封面图应为 null");
    }

    private static CardImageStub img(int position, String url) {
        return new CardImageStub(position, url);
    }

    private record CardImageStub(Integer position, String url)
            implements CardStructurePlanner.CardImageRef {
        @Override
        public Integer getPosition() { return position; }
        @Override
        public String getUrl() { return url; }
    }
}
