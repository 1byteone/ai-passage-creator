package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.mapper.ComicBookMapper;
import com.example.aipassagecreator.mapper.ComicEpisodeMapper;
import com.example.aipassagecreator.mapper.ComicMonthlyVolumeMapper;
import com.example.aipassagecreator.model.po.ComicBookPo;
import com.example.aipassagecreator.model.po.ComicEpisodePo;
import com.example.aipassagecreator.model.po.ComicMonthlyVolumePo;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.service.AgnesImageService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 漫画手帐端到端集成测试 — 模拟 skill 输出 → ComicJournalService.processAsync → 落库断言。
 *
 * <p>comic_episode 无 user_id 列（经 comic_book 关联），断言按 book 关联校正：
 * 先查 comic_book（user_id=2L + 默认档案名）→ 取 bookId → 查 episode / monthly_volume。</p>
 *
 * <p>离线确定性：用 @TestConfiguration @Primary 替换 AgnesImageService 为预配置 mock
 * （getMethod 返回 AGNES 避免 ImageServiceStrategy.init 的 EnumMap NPE；searchImage 返回固定 URL，
 * 不发起真实 API 调用）。playwright.enabled=false 跳过 PNG 渲染（renderToPngDataUrl 返回 null，
 * pngUrl 降级为生图 URL）。processAsync 为 @Async("ragExecutor")，测试轮询 H2 等待异步落库完成。</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:comic_e2e;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:sql/h2-schema.sql",
        "spring.session.store-type=none",
        "playwright.enabled=false"
})
@ActiveProfiles("test")
@DirtiesContext
@Import(ComicEndToEndIntegrationTest.ComicE2EImageMockConfig.class)
class ComicEndToEndIntegrationTest {

    @Autowired
    private ComicJournalService comicJournalService;

    @Autowired
    private ComicBookMapper bookMapper;

    @Autowired
    private ComicEpisodeMapper episodeMapper;

    @Autowired
    private ComicMonthlyVolumeMapper volumeMapper;

    @Test
    @DisplayName("skill 输出 → 渲染 HTML → 落库 episode + 月册聚合（按 book 关联断言）")
    void processAsync_persistsEpisodeAndVolume() {
        SkillExecutionPo po = new SkillExecutionPo();
        po.setSkillExecutionId("e2e-1");
        po.setUserId(2L);
        po.setSkillName("comic-journal");

        // routeResult 无 bookName → ComicJournalService 使用默认档案名「我的生活手帐」
        Map<String, Object> output = Map.of(
                "routeResult", Map.of("type", "daily", "title", "晨跑", "summary", "s", "beats", List.of(), "tone", "t"),
                "storyboardResult", Map.of("mode", "panels", "panels", List.of(
                        Map.of("panelNo", 1, "composition", "全景", "content", "c", "emotion", "e", "captionText", "清晨"))),
                "imagePrompts", Map.of("imagePrompts", List.of(Map.of("panelNo", 1, "prompt", "清晨阳光下的街道"))),
                "layoutResult", Map.of("cover", Map.of("title", "晨跑", "subtitle", "s", "tone", "t"),
                        "sections", List.of(), "textBlocks", List.of(), "imagePlacements", List.of()));

        comicJournalService.processAsync(po, output, 2L);

        // comic_episode 无 user_id 列，先按 user_id 找档案再取 bookId
        // 异步先插 book → 再插 episode → 再 upsert 月册，故逐个轮询到落库完成
        ComicBookPo book = awaitBook(2L, "我的生活手帐");
        Long bookId = book.getId();

        List<ComicEpisodePo> episodes = awaitEpisodes(bookId);
        assertEquals(1, episodes.size());
        ComicEpisodePo episode = episodes.get(0);
        assertEquals("晨跑", episode.getTitle());
        assertNotNull(episode.getPageHtml());
        assertFalse(episode.getPageHtml().isBlank(), "pageHtml 应为非空渲染 HTML");
        assertTrue(episode.getPageHtml().contains("晨跑"), "渲染 HTML 应包含手帐标题");
        // playwright.enabled=false → PNG 跳过 → pngUrl 降级为生图 URL
        assertEquals("https://cos/test-comic.png", episode.getPngUrl());

        List<ComicMonthlyVolumePo> volumes = awaitVolumes(bookId);
        assertEquals(1, volumes.size());
        ComicMonthlyVolumePo volume = volumes.get(0);
        assertEquals(1, volume.getEpisodeCount());
        assertNotNull(volume.getIndexHtml());
        assertFalse(volume.getIndexHtml().isBlank(), "月册 indexHtml 应为非空渲染 HTML");
    }

    /**
     * 替换真实生图服务：预配置 getMethod（ImageServiceStrategy.init 需要）+
     * searchImage 返回固定 URL。@Primary 使 ComicJournalService 构造注入命中 mock。
     */
    @TestConfiguration
    static class ComicE2EImageMockConfig {

        @Bean
        @Primary
        AgnesImageService mockAgnesImageService() {
            AgnesImageService mock = mock(AgnesImageService.class);
            when(mock.getMethod()).thenReturn(ImageMethodEnum.AGNES);
            when(mock.searchImage(any())).thenReturn("https://cos/test-comic.png");
            return mock;
        }
    }

    /** 轮询等待 @Async 落库完成（上限 10s，同一进程内 H2 内存库可见） */
    private ComicBookPo awaitBook(Long userId, String bookName) {
        return await(Duration.ofSeconds(10), () -> bookMapper.selectOneByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("book_name", bookName)));
    }

    /** 轮询等待章节落库（空列表视为未完成，返回 null 继续等） */
    private List<ComicEpisodePo> awaitEpisodes(Long bookId) {
        return await(Duration.ofSeconds(10), () -> {
            List<ComicEpisodePo> eps = episodeMapper.selectListByQuery(
                    QueryWrapper.create().eq("book_id", bookId));
            return eps.isEmpty() ? null : eps;
        });
    }

    /** 轮询等待月册落库（空列表视为未完成，返回 null 继续等） */
    private List<ComicMonthlyVolumePo> awaitVolumes(Long bookId) {
        return await(Duration.ofSeconds(10), () -> {
            List<ComicMonthlyVolumePo> vols = volumeMapper.selectListByQuery(
                    QueryWrapper.create().eq("book_id", bookId));
            return vols.isEmpty() ? null : vols;
        });
    }

    private <T> T await(Duration timeout, Supplier<T> supplier) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            T value = supplier.get();
            if (value != null) {
                return value;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待异步落库被中断", e);
            }
        }
        return fail("等待异步落库超时: " + timeout);
    }
}
