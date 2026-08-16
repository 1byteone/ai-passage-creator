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

    private Map<String, Object> output() {
        return Map.of(
                "routeResult", Map.of("type", "daily", "title", "雨中散步", "summary", "s", "beats", List.of(), "tone", "t"),
                "storyboardResult", Map.of("mode", "panels", "panels", List.of(
                        Map.of("panelNo", 1, "composition", "近景", "content", "c", "emotion", "e", "captionText", "雨停了"))),
                "imagePrompts", Map.of("imagePrompts", List.of(Map.of("panelNo", 1, "prompt", "p"))),
                "layoutResult", Map.of("cover", Map.of("title", "雨中散步", "subtitle", "s", "tone", "t"),
                        "sections", List.of(), "textBlocks", List.of(), "imagePlacements", List.of()));
    }

    @Test
    void processAsync_generatesImageAndPersistsEpisodeAndVolume() {
        when(agnesImageService.searchImage(any())).thenReturn("https://cos/img.png");
        when(templateEngine.renderEpisode(any(), any(), any(), any(), any(), any())).thenReturn("<html>ok</html>");
        when(bookMapper.selectOneByQuery(any())).thenReturn(null); // 无档案则新建
        when(volumeMapper.selectOneByQuery(any())).thenReturn(null);

        service.processAsync(po(), output(), 1L);

        verify(bookMapper).insert(any(ComicBookPo.class));
        ArgumentCaptor<ComicEpisodePo> epCaptor = ArgumentCaptor.forClass(ComicEpisodePo.class);
        verify(episodeMapper).insert(epCaptor.capture());
        assertEquals("daily", epCaptor.getValue().getInputType());
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

        service.processAsync(po(), output(), 1L);

        ArgumentCaptor<ComicEpisodePo> epCaptor = ArgumentCaptor.forClass(ComicEpisodePo.class);
        verify(episodeMapper).insert(epCaptor.capture());
        // 生图失败降级为静态占位（渲染仍成功，pageHtml 存在）
        assertTrue(epCaptor.getValue().getPageHtml().contains("<html>"));
    }
}
