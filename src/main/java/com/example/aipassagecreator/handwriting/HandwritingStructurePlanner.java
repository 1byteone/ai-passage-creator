package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 手写纸张分页器。
 * A4 比例页面（~1300 中文字符/页），按段落边界断页。
 */
@Slf4j
@Component
public class HandwritingStructurePlanner {

    private static final int MAX_CHARS_PER_PAGE = 1300;
    private static final int MAX_PAGES = 30;

    public List<HandwritingPage> plan(String content, String title) {
        if (content == null || content.isBlank()) {
            return List.of(new HandwritingPage(1, title, ""));
        }

        String[] paragraphs = content.split("\\n\\s*\\n");
        List<HandwritingPage> pages = new ArrayList<>();
        StringBuilder currentPage = new StringBuilder();
        int pageNo = 1;

        if (title != null && !title.isBlank()) {
            currentPage.append("# ").append(title).append("\n\n");
        }

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (currentPage.length() > 0 &&
                    currentPage.length() + trimmed.length() > MAX_CHARS_PER_PAGE) {
                pages.add(new HandwritingPage(pageNo++, null, currentPage.toString().trim()));
                currentPage.setLength(0);
            }

            if (currentPage.length() > 0) {
                currentPage.append("\n\n");
            }
            currentPage.append(trimmed);
        }

        if (!currentPage.isEmpty()) {
            pages.add(new HandwritingPage(pageNo, null, currentPage.toString().trim()));
        }

        if (pages.size() > MAX_PAGES) {
            log.warn("手写分页超出上限: pages={}, max={}", pages.size(), MAX_PAGES);
            return pages.subList(0, MAX_PAGES);
        }

        return pages;
    }
}
