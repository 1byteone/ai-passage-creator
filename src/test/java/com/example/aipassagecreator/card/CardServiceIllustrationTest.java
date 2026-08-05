package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.illustration.IllustrationImageService;
import com.example.aipassagecreator.card.model.ComplianceReport;
import com.example.aipassagecreator.card.model.PagePlan;
import com.example.aipassagecreator.card.model.PageResult;
import com.example.aipassagecreator.service.CosService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CardService 插画风格测试 — illustration 封面注入插画图 URL。
 * <p>mock {@link IllustrationImageService} 返回静态 classpath 素材 URL，断言封面页
 * imageUrl 被覆写为插画 URL。base64 内联交由 {@link CardTemplateEngine}/{@link CardImageResolver}
 * 统一处理，不在本层转码。沿用 {@link CardServiceTest} 的 Mockito 单元测试模式。</p>
 */
@ExtendWith(MockitoExtension.class)
class CardServiceIllustrationTest {

    private static final String TASK_ID = "task-illus";

    @Mock
    private CardStructurePlanner planner;

    @Mock
    private CardTemplateEngine templateEngine;

    @Mock
    private CardRenderPipeline renderPipeline;

    @Mock
    private CardComplianceChecker complianceChecker;

    @Mock
    private CardPageMapper cardPageMapper;

    @Mock
    private CosService cosService;

    @Mock
    private IllustrationImageService illustrationImageService;

    @InjectMocks
    private CardService cardService;

    @Test
    @DisplayName("illustration 风格：封面页 imageUrl 被覆写为插画 URL")
    void generate_illustrationStyle_setsCoverImageUrl() {
        String illusUrl = "classpath:illustration/healing/healing-1.png";
        List<PagePlan> pages = List.of(
                PagePlan.builder().pageNo(1).pageType("COVER").contentMd("第一页").build(),
                PagePlan.builder().pageNo(2).pageType("CONTENT").contentMd("第二页").build());

        when(planner.plan(any(), any(), any(), any(), any())).thenReturn(pages);
        when(illustrationImageService.generateCoverImage(any(), any())).thenReturn(illusUrl);
        when(templateEngine.render(any(), any())).thenReturn(List.of("<h1>1</h1>", "<h1>2</h1>"));
        when(renderPipeline.render(any(), eq(TASK_ID))).thenReturn(List.of(okResult(), okResult()));
        when(complianceChecker.textCheck(any(), any(), any()))
                .thenReturn(ComplianceReport.builder().passed(true).build());
        when(cardPageMapper.deleteByQuery(any())).thenReturn(1);
        when(cardPageMapper.insert(any())).thenReturn(1);
        when(cosService.uploadToKey(any(), any(), any())).thenReturn("uploaded");
        when(cosService.generatePresignedUrl(any())).thenReturn("http://presigned/cover.png");

        cardService.generate("正文内容", "标题", "副标题", null,
                "illustration", "healing", TASK_ID, "default");

        ArgumentCaptor<List<PagePlan>> captor = ArgumentCaptor.forClass(List.class);
        verify(templateEngine).render(captor.capture(), anyString());
        PagePlan cover = captor.getValue().stream()
                .filter(p -> "COVER".equals(p.getPageType()))
                .findFirst().orElseThrow();
        assertEquals(illusUrl, cover.getImageUrl(), "封面页 imageUrl 应为插画 URL");
    }

    @Test
    @DisplayName("非 illustration 风格：不调用插画生成，封面 imageUrl 保持原样")
    void generate_nonIllustration_skipsCoverInjection() {
        String originalUrl = "http://original/cover.png";
        List<PagePlan> pages = List.of(
                PagePlan.builder().pageNo(1).pageType("COVER").contentMd("第一页").imageUrl(originalUrl).build(),
                PagePlan.builder().pageNo(2).pageType("CONTENT").contentMd("第二页").build());

        when(planner.plan(any(), any(), any(), any(), any())).thenReturn(pages);
        when(templateEngine.render(any(), any())).thenReturn(List.of("<h1>1</h1>", "<h1>2</h1>"));
        when(renderPipeline.render(any(), eq(TASK_ID))).thenReturn(List.of(okResult(), okResult()));
        when(complianceChecker.textCheck(any(), any(), any()))
                .thenReturn(ComplianceReport.builder().passed(true).build());
        when(cardPageMapper.deleteByQuery(any())).thenReturn(1);
        when(cardPageMapper.insert(any())).thenReturn(1);
        when(cosService.uploadToKey(any(), any(), any())).thenReturn("uploaded");
        when(cosService.generatePresignedUrl(any())).thenReturn("http://presigned/cover.png");

        cardService.generate("正文内容", "标题", "副标题", null,
                "warm", "healing", TASK_ID, "default");

        verify(illustrationImageService, never()).generateCoverImage(any(), any());
        ArgumentCaptor<List<PagePlan>> captor = ArgumentCaptor.forClass(List.class);
        verify(templateEngine).render(captor.capture(), anyString());
        PagePlan cover = captor.getValue().stream()
                .filter(p -> "COVER".equals(p.getPageType()))
                .findFirst().orElseThrow();
        assertEquals(originalUrl, cover.getImageUrl(), "非 illustration 风格不应覆写封面 imageUrl");
    }

    private PageResult okResult() {
        return PageResult.builder().pngBytes(new byte[]{1, 2, 3}).layoutPassed(true).renderMs(5).build();
    }
}
