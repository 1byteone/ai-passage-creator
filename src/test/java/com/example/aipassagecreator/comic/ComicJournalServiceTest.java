package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.mapper.ComicBookMapper;
import com.example.aipassagecreator.mapper.ComicEpisodeMapper;
import com.example.aipassagecreator.mapper.ComicMonthlyVolumeMapper;
import com.example.aipassagecreator.model.po.ComicBookPo;
import com.example.aipassagecreator.model.po.ComicEpisodePo;
import com.example.aipassagecreator.model.po.ComicMonthlyVolumePo;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.example.aipassagecreator.service.AgnesImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComicJournalServiceTest {

    @Mock private AgnesImageService agnesImageService;
    @Mock private ComicTemplateEngine templateEngine;
    @Mock private ComicRenderService renderService;
    @Mock private ComicBookMapper bookMapper;
    @Mock private ComicEpisodeMapper episodeMapper;
    @Mock private ComicMonthlyVolumeMapper volumeMapper;

    @InjectMocks private ComicJournalService service;

    private SkillExecutionPo po() {
        SkillExecutionPo po = new SkillExecutionPo();
        po.setUserId(1L);
        po.setSkillExecutionId("exec-1");
        return po;
    }

    private Map<String, Object> output(String style, String bookName) {
        return output(style, bookName, "雨中散步");
    }

    private Map<String, Object> output(String style, String bookName, String title) {
        Map<String, Object> route = new java.util.HashMap<>(Map.of(
                "type", "daily", "title", title, "summary", "s", "beats", List.of(), "tone", "t"));
        if (style != null) {
            route.put("style", style);
        }
        if (bookName != null) {
            route.put("bookName", bookName);
        }
        return Map.of(
                "routeResult", route,
                "storyboardResult", Map.of("mode", "panels", "panels", List.of(
                        Map.of("panelNo", 1, "composition", "近景", "content", "c", "emotion", "e", "captionText", "雨停了"))),
                "imagePrompts", Map.of("imagePrompts", List.of(Map.of("panelNo", 1, "prompt", "p"))),
                "layoutResult", Map.of("cover", Map.of("title", title, "subtitle", "s", "tone", "t"),
                        "sections", List.of(), "textBlocks", List.of(), "imagePlacements", List.of()));
    }

    @Test
    void processAsync_generatesImageAndPersistsEpisodeAndVolume() {
        when(agnesImageService.searchImage(any())).thenReturn("https://cos/img.png");
        when(templateEngine.renderEpisode(any(), any(), any(), any(), any(), any())).thenReturn("<html>ok</html>");
        when(bookMapper.selectOneByQuery(any())).thenReturn(null); // 无档案则新建
        when(volumeMapper.selectOneByQuery(any())).thenReturn(null);

        service.processAsync(po(), output("inkwash", "晨跑手帐"), 1L);

        // phase1 routeResult 透传 style/bookName → 新建档案带画风与档案名
        ArgumentCaptor<ComicBookPo> bookCaptor = ArgumentCaptor.forClass(ComicBookPo.class);
        verify(bookMapper).insert(bookCaptor.capture());
        assertEquals("晨跑手帐", bookCaptor.getValue().getBookName());
        assertEquals("inkwash", bookCaptor.getValue().getDefaultStyle());

        ArgumentCaptor<ComicEpisodePo> epCaptor = ArgumentCaptor.forClass(ComicEpisodePo.class);
        verify(episodeMapper).insert(epCaptor.capture());
        assertEquals("daily", epCaptor.getValue().getInputType());
        assertEquals("inkwash", epCaptor.getValue().getStyle());
        assertNotNull(epCaptor.getValue().getYearMonth());
        assertTrue(epCaptor.getValue().getYearMonth().matches("\\d{4}-\\d{2}"), "yearMonth 应为 YYYY-MM");
        assertTrue(epCaptor.getValue().getPageHtml().contains("<html>"));
        assertEquals("https://cos/img.png", epCaptor.getValue().getPngUrl());
        verify(volumeMapper).insert(any(ComicMonthlyVolumePo.class));
    }

    @Test
    void processAsync_imageGenFails_keepsStaticFallback() {
        when(agnesImageService.searchImage(any())).thenReturn(null); // 生图失败
        when(templateEngine.renderEpisode(any(), any(), any(), any(), any(), any())).thenReturn("<html>ok</html>");
        when(bookMapper.selectOneByQuery(any())).thenReturn(null);
        when(volumeMapper.selectOneByQuery(any())).thenReturn(null);

        // routeResult 无 style/bookName → 回退默认画风与默认档案名
        service.processAsync(po(), output(null, null), 1L);

        ArgumentCaptor<ComicBookPo> bookCaptor = ArgumentCaptor.forClass(ComicBookPo.class);
        verify(bookMapper).insert(bookCaptor.capture());
        assertEquals("我的生活手帐", bookCaptor.getValue().getBookName());
        assertEquals("powder", bookCaptor.getValue().getDefaultStyle());

        ArgumentCaptor<ComicEpisodePo> epCaptor = ArgumentCaptor.forClass(ComicEpisodePo.class);
        verify(episodeMapper).insert(epCaptor.capture());
        assertEquals("powder", epCaptor.getValue().getStyle());
        // 生图失败降级为静态占位（渲染仍成功，pageHtml 存在）
        assertTrue(epCaptor.getValue().getPageHtml().contains("<html>"));
    }

    @Test
    void processAsync_twoEpisodesSameMonth_rerendersMonthlyIndexWithAllEpisodes() {
        when(agnesImageService.searchImage(any())).thenReturn("https://cos/img.png");
        when(templateEngine.renderEpisode(any(), any(), any(), any(), any(), any())).thenReturn("<html>ok</html>");
        ComicBookPo book = new ComicBookPo();
        book.setId(1L);
        when(bookMapper.selectOneByQuery(any())).thenReturn(book);

        // renderMonthly 回显传入的章节标题，证明月册按当月全部章节重渲染（而非只留第一章）
        when(templateEngine.renderMonthly(any(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> eps = inv.getArgument(2);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> ep : eps) {
                sb.append(ep.get("title")).append('|');
            }
            return sb.toString();
        });

        // 章节号递增 + 记录已插入的 episode，供月册更新分支按当月查询
        int[] episodeNo = new int[1];
        when(episodeMapper.selectCountByQuery(any())).thenAnswer(inv -> (long) episodeNo[0]++);
        List<ComicEpisodePo> insertedEpisodes = new ArrayList<>();
        doAnswer(inv -> {
            ComicEpisodePo ep = inv.getArgument(0);
            ep.setId((long) insertedEpisodes.size() + 1);
            insertedEpisodes.add(ep);
            return 1;
        }).when(episodeMapper).insert(any(ComicEpisodePo.class));
        when(episodeMapper.selectListByQuery(any())).thenAnswer(inv -> new ArrayList<>(insertedEpisodes));

        // 月册：第一次 selectOneByQuery=null → insert；第二次返回已插入的 volume → update
        ComicMonthlyVolumePo[] volumeHolder = new ComicMonthlyVolumePo[1];
        when(volumeMapper.selectOneByQuery(any())).thenAnswer(inv -> volumeHolder[0]);
        doAnswer(inv -> {
            volumeHolder[0] = inv.getArgument(0);
            return 1;
        }).when(volumeMapper).insert(any(ComicMonthlyVolumePo.class));

        // 同一档案同月两集：晨跑（episodeNo=1）→ 午后（episodeNo=2）
        service.processAsync(po(), output("inkwash", "晨跑手帐", "晨跑"), 1L);
        service.processAsync(po(), output("inkwash", "晨跑手帐", "午后"), 1L);

        verify(volumeMapper).insert(any(ComicMonthlyVolumePo.class));

        // 更新分支必须重渲染全部章节：indexHtml 同时含两集标题，episode_count=2
        ArgumentCaptor<ComicMonthlyVolumePo> volumeCaptor = ArgumentCaptor.forClass(ComicMonthlyVolumePo.class);
        verify(volumeMapper).update(volumeCaptor.capture());
        ComicMonthlyVolumePo updated = volumeCaptor.getValue();
        assertTrue(updated.getIndexHtml().contains("晨跑"), "重渲染后 indexHtml 应含第一集标题");
        assertTrue(updated.getIndexHtml().contains("午后"), "重渲染后 indexHtml 应含第二集标题");
        assertEquals(2, updated.getEpisodeCount());
    }
}
