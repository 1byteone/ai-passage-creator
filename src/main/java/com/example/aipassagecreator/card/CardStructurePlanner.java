package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.PagePlan;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CardStructurePlanner {

    private static final int MAX_PAGES = 20;
    private static final int MAX_CHARS_PER_PAGE = 500;
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{\\s*(IMAGE|ICON)_PLACEHOLDER_\\d+\\s*\\}\\}");

    /**
     * 配图最小抽象（position=1 为封面，其余为内容页插图）。
     * 由调用方提供（如 {@code ArticleState.ImageResult}），planner 不依赖具体类型。
     */
    public interface CardImageRef {
        Integer getPosition();
        String getUrl();
    }

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    /**
     * 文章正文 → 分页结构（无配图）。
     * 纯函数：相同输入 → 相同输出。
     */
    public List<PagePlan> plan(String fullContent, String mainTitle,
                               String subTitle, String coverImage) {
        return plan(fullContent, mainTitle, subTitle, coverImage, List.of());
    }

    /**
     * 文章正文 → 分页结构（含配图）。
     * 配图按 position 挂到各页（第 1 页为封面），供模板渲染。
     *
     * @param images 文章配图列表（position=1 为封面图）
     */
    public List<PagePlan> plan(String fullContent, String mainTitle,
                               String subTitle, String coverImage,
                               List<? extends CardImageRef> images) {
        if (fullContent == null || fullContent.isBlank()) {
            throw new IllegalArgumentException("文章内容为空，无法生成卡片");
        }
        // 1. 清理残留占位符
        String clean = PLACEHOLDER_PATTERN.matcher(fullContent).replaceAll("");

        // 2. 按 position 建立配图映射（内容页：position>1）
        java.util.Map<Integer, String> imageByPosition = new java.util.HashMap<>();
        String cover = coverImage;
        if (cover == null || cover.isBlank()) {
            for (CardImageRef img : images) {
                if (img.getPosition() != null && img.getPosition() == 1 && img.getUrl() != null) {
                    cover = img.getUrl();
                    break;
                }
            }
        }
        for (CardImageRef img : images) {
            if (img.getPosition() != null && img.getPosition() > 1 && img.getUrl() != null) {
                imageByPosition.put(img.getPosition(), img.getUrl());
            }
        }

        // 3. 按 flexmark AST 块级切割
        List<PagePlan> pages = new ArrayList<>();
        pages.add(createCoverPage(mainTitle, subTitle, cover));

        // 4. 按 ## 标题分页
        String[] sections = clean.split("(?m)(?=^## )", -1);
        for (String section : sections) {
            if (section.trim().isEmpty()) continue;
            // 提取标题行
            String title = "";
            String body = section;
            // 检查是否不含 ## 标题的首段
            if (section.startsWith("## ")) {
                int nl = section.indexOf('\n');
                if (nl > 0) {
                    title = section.substring(3, nl).trim();
                    body = section.substring(nl).trim();
                } else {
                    title = section.substring(3).trim();
                    body = "";
                }
            }
            // 超长拆页（500 字上限）
            List<String> chunks = splitByLength(body, MAX_CHARS_PER_PAGE);
            int pageNoBefore = pages.size();
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                String html = renderer.render(parser.parse(chunk));
                // 该页对应配图：按页号在整体序列中的位置映射
                String pageImage = imageByPosition.get(pageNoBefore + i + 1);
                pages.add(PagePlan.builder()
                        .pageNo(pages.size() + 1)
                        .pageType("CONTENT")
                        .title(title)
                        .contentMd(chunk)
                        .contentHtml(html)
                        .imageUrl(pageImage)
                        .build());
            }
        }
        if (pages.size() > MAX_PAGES) {
            throw new IllegalArgumentException(
                    "文章过长，超出最大页数限制: " + MAX_PAGES);
        }
        return pages;
    }

    private PagePlan createCoverPage(String mainTitle, String subTitle, String coverImage) {
        String title = mainTitle != null ? mainTitle : "";
        String sub = subTitle != null ? subTitle : "";
        String md = "# " + title + "\n\n" + sub;
        if (coverImage != null && !coverImage.isBlank()) {
            md += "\n\n![](" + coverImage + ")";
        }
        return PagePlan.builder()
                .pageNo(1).pageType("COVER")
                .title(title).contentMd(md)
                .contentHtml(renderer.render(parser.parse(md)))
                .imageUrl(coverImage)
                .build();
    }

    /**
     * 按字符数拆分，以句号/换行为断点。
     */
    private List<String> splitByLength(String text, int maxLen) {
        List<String> chunks = new ArrayList<>();
        while (text.length() > maxLen) {
            int split = text.lastIndexOf('。', maxLen);
            if (split < 1) split = text.lastIndexOf('\n', maxLen);
            if (split < 1) split = maxLen;
            chunks.add(text.substring(0, split).trim());
            text = text.substring(split).trim();
        }
        if (!text.isEmpty()) chunks.add(text);
        return chunks;
    }
}
