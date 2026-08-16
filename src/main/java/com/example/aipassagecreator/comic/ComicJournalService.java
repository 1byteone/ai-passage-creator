package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.mapper.ComicBookMapper;
import com.example.aipassagecreator.mapper.ComicEpisodeMapper;
import com.example.aipassagecreator.mapper.ComicMonthlyVolumeMapper;
import com.example.aipassagecreator.model.po.ComicBookPo;
import com.example.aipassagecreator.model.po.ComicEpisodePo;
import com.example.aipassagecreator.model.po.ComicMonthlyVolumePo;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.service.AgnesImageService;
import com.example.aipassagecreator.utils.GsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 漫画手帐产出服务：skill SUCCESS 后异步消费 4 阶段 JSON 输出，
 * 生图 → 渲染 HTML → 可选 PNG → 落库（episode + 月册聚合 + 档案）。
 */
@Slf4j
@Service
public class ComicJournalService {

    private static final String DEFAULT_STYLE = "powder";
    private static final String STATIC_PLACEHOLDER = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(
            ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"600\" height=\"600\">"
                    + "<rect width=\"600\" height=\"600\" fill=\"#f0f0f0\"/>"
                    + "<text x=\"300\" y=\"300\" text-anchor=\"middle\" fill=\"#999\">图</text>"
                    + "</svg>").getBytes(StandardCharsets.UTF_8));

    private final AgnesImageService agnesImageService;
    private final ComicTemplateEngine templateEngine;
    private final ComicRenderService renderService;
    private final ComicBookMapper bookMapper;
    private final ComicEpisodeMapper episodeMapper;
    private final ComicMonthlyVolumeMapper volumeMapper;

    public ComicJournalService(AgnesImageService agnesImageService, ComicTemplateEngine templateEngine,
                               ComicRenderService renderService, ComicBookMapper bookMapper,
                               ComicEpisodeMapper episodeMapper, ComicMonthlyVolumeMapper volumeMapper) {
        this.agnesImageService = agnesImageService;
        this.templateEngine = templateEngine;
        this.renderService = renderService;
        this.bookMapper = bookMapper;
        this.episodeMapper = episodeMapper;
        this.volumeMapper = volumeMapper;
    }

    @Async("ragExecutor")
    public void processAsync(SkillExecutionPo po, Map<String, Object> output, Long userId) {
        try {
            process(po, output, userId);
        } catch (Exception e) {
            log.error("漫画手帐产出失败: executionId={}", po.getSkillExecutionId(), e);
        }
    }

    private void process(SkillExecutionPo po, Map<String, Object> output, Long userId) {
        Map<String, Object> route = map(output.get("routeResult"));
        Map<String, Object> storyboard = map(output.get("storyboardResult"));
        Map<String, Object> imagePrompts = map(output.get("imagePrompts"));
        Map<String, Object> layout = map(output.get("layoutResult"));

        String styleName = str(route.get("style"), DEFAULT_STYLE);
        ComicStyle style = ComicStyle.from(styleName);
        String bookName = str(route.get("bookName"), "我的生活手帐");

        // 1) 档案（不存在则建）
        ComicBookPo book = findBook(userId, bookName);
        if (book == null) {
            book = new ComicBookPo();
            book.setUserId(userId);
            book.setBookName(bookName);
            book.setDefaultStyle(style.getName());
            book.setIsDelete(0);
            bookMapper.insert(book);
        }

        // 2) 生图（每格一张，失败降级静态占位）
        List<String> imageUrls = generateImages(imagePrompts);

        // 3) 渲染 HTML
        List<Map<String, Object>> panels = extractPanels(storyboard);
        List<Map<String, Object>> textBlocks = extractTextBlocks(layout);
        String title = extractTitle(route, layout);
        String html = templateEngine.renderEpisode(
                style, title, panels, imageUrls, textBlocks, extractSubtitle(layout));

        // 4) 可选 PNG（渲染引擎不可用时降级为封面图 URL，保证 episode 始终有可展示图）
        String pngDataUrl = renderService.renderToPngDataUrl(html, po.getSkillExecutionId());
        String pngUrl = pngDataUrl != null ? pngDataUrl
                : (imageUrls.isEmpty() ? null : imageUrls.get(0));

        // 5) 落库 episode（yearMonth 与月册聚合共用同一值，保证同档可按月过滤）
        String yearMonth = LocalDateTime.now().toString().substring(0, 7);
        ComicEpisodePo episode = new ComicEpisodePo();
        episode.setBookId(book.getId());
        episode.setEpisodeNo(nextEpisodeNo(book.getId()));
        episode.setTitle(title);
        episode.setInputType(str(route.get("type"), "daily"));
        episode.setInputSummary(str(route.get("summary"), ""));
        episode.setStyle(style.getName());
        episode.setYearMonth(yearMonth);
        episode.setRouteResult(json(route));
        episode.setStoryboardResult(json(storyboard));
        episode.setImagePrompts(json(imagePrompts));
        episode.setLayoutResult(json(layout));
        episode.setPageHtml(html);
        episode.setPngUrl(pngUrl);
        episode.setIsDelete(0);
        episodeMapper.insert(episode);

        // 6) 月册聚合
        upsertMonthlyVolume(book.getId(), episode, yearMonth);

        log.info("漫画手帐产出完成: episodeId={}, bookId={}, type={}, style={}",
                episode.getId(), book.getId(), episode.getInputType(), style.getName());
    }

    private List<String> generateImages(Map<String, Object> imagePrompts) {
        List<Map<String, Object>> prompts = list(imagePrompts.get("imagePrompts"));
        List<String> urls = new ArrayList<>();
        for (Map<String, Object> item : prompts) {
            String prompt = str(item.get("prompt"), "");
            String url = null;
            if (!prompt.isBlank()) {
                url = agnesImageService.searchImage(prompt);
            }
            urls.add(url != null && !url.isBlank() ? url : STATIC_PLACEHOLDER);
        }
        return urls;
    }

    private ComicBookPo findBook(Long userId, String bookName) {
        return bookMapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .eq("user_id", userId).eq("book_name", bookName).eq("is_delete", 0));
    }

    private int nextEpisodeNo(Long bookId) {
        Long max = episodeMapper.selectCountByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .eq("book_id", bookId).eq("is_delete", 0));
        return max == null ? 1 : (int) (max + 1);
    }

    private void upsertMonthlyVolume(Long bookId, ComicEpisodePo episode, String yearMonth) {
        ComicMonthlyVolumePo volume = volumeMapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .eq("book_id", bookId).eq("year_month", yearMonth).eq("is_delete", 0));
        if (volume == null) {
            volume = new ComicMonthlyVolumePo();
            volume.setBookId(bookId);
            volume.setYearMonth(yearMonth);
            volume.setEpisodeCount(1);
            volume.setCoverTitle(yearMonth + " 手帐");
            volume.setIndexHtml(templateEngine.renderMonthly(
                    ComicStyle.from(episode.getStyle()),
                    yearMonth + " 手帐",
                    List.of(Map.of("title", episode.getTitle(), "episodeNo", episode.getEpisodeNo()))));
            volume.setIsDelete(0);
            volumeMapper.insert(volume);
        } else {
            // 已有月册：按当月全部章节重渲染 index_html，否则连续阅读页永远只有第一章，
            // episode_count 与 index_html 在第二集后开始分叉
            try {
                List<Map<String, Object>> episodeMaps = new ArrayList<>();
                List<ComicEpisodePo> monthEpisodes = episodeMapper.selectListByQuery(
                        com.mybatisflex.core.query.QueryWrapper.create()
                                .eq("book_id", bookId)
                                .eq("year_month", yearMonth)
                                .eq("is_delete", 0)
                                .orderBy("episode_no", true));
                for (ComicEpisodePo ep : monthEpisodes) {
                    episodeMaps.add(Map.of("title", ep.getTitle(), "episodeNo", ep.getEpisodeNo()));
                }
                volume.setIndexHtml(templateEngine.renderMonthly(
                        ComicStyle.from(episode.getStyle()),
                        yearMonth + " 手帐",
                        episodeMaps));
                volume.setEpisodeCount(monthEpisodes.size());
            } catch (Exception e) {
                // 重渲染失败保留旧 index_html 与旧计数，不阻断本集落库（本集已在上一步插入）
                log.warn("月册 indexHtml 重渲染失败，保留旧值: bookId={}, yearMonth={}", bookId, yearMonth, e);
            }
            volume.setUpdateTime(LocalDateTime.now());
            volumeMapper.update(volume);
        }
    }

    private static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    private static List<Map<String, Object>> list(Object o) {
        if (!(o instanceof List)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) o) {
            if (item instanceof Map) result.add((Map<String, Object>) item);
        }
        return result;
    }

    private static String str(Object o, String fallback) {
        return o == null || o.toString().isBlank() ? fallback : o.toString();
    }

    private static String json(Object o) {
        return GsonUtils.toJson(o);
    }

    private static List<Map<String, Object>> extractPanels(Map<String, Object> storyboard) {
        return list(storyboard.get("panels"));
    }

    private static List<Map<String, Object>> extractTextBlocks(Map<String, Object> layout) {
        return list(layout.get("textBlocks"));
    }

    private static String extractTitle(Map<String, Object> route, Map<String, Object> layout) {
        String title = str(route.get("title"), "");
        if (!title.isBlank()) return title;
        Map<String, Object> cover = map(layout.get("cover"));
        return str(cover.get("title"), "未命名手帐");
    }

    private static String extractSubtitle(Map<String, Object> layout) {
        Map<String, Object> cover = map(layout.get("cover"));
        return str(cover.get("subtitle"), "");
    }
}
