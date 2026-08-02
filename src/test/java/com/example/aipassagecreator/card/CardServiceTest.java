package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.PagePlan;
import com.example.aipassagecreator.card.model.PageResult;
import com.example.aipassagecreator.service.CosService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CardService 单元测试 — 聚焦预览与正式生成的 COS key 隔离（S3-5）。
 *
 * <p>preview 用 {@code cards/{taskId}/preview/{pageNo}.png}，generate 用
 * {@code cards/{taskId}/{pageNo}.png}，防止预览覆盖正式卡片产物。</p>
 */
@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    private static final String TASK_ID = "task-s3-5";

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

    @InjectMocks
    private CardService cardService;

    private List<PagePlan> threePages() {
        return List.of(
                PagePlan.builder().pageNo(1).pageType("COVER").contentMd("第一页").build(),
                PagePlan.builder().pageNo(2).pageType("CONTENT").contentMd("第二页").build(),
                PagePlan.builder().pageNo(3).pageType("CONTENT").contentMd("第三页").build());
    }

    private PageResult okResult() {
        return PageResult.builder().pngBytes(new byte[]{1, 2, 3}).layoutPassed(true).renderMs(5).build();
    }

    @Test
    @DisplayName("预览上传到 preview 前缀，不与正式 key 冲突")
    void preview_usesIsolatedCosKey() {
        List<PagePlan> pages = threePages();
        when(planner.plan(any(), any(), any(), any())).thenReturn(pages);
        when(templateEngine.render(any(), any())).thenReturn(List.of("<h1>1</h1>", "<h1>2</h1>"));
        when(renderPipeline.render(any(), eq(TASK_ID))).thenReturn(List.of(okResult(), okResult()));
        when(cosService.generatePresignedUrl(any())).thenReturn("http://presigned/preview.png");

        cardService.preview("正文内容", "标题", "副标题", null, "warm", TASK_ID);

        verify(cosService, times(1)).uploadToKey(any(), any(), eq("cards/" + TASK_ID + "/preview/1.png"));
        verify(cosService, times(1)).uploadToKey(any(), any(), eq("cards/" + TASK_ID + "/preview/2.png"));
        verify(cosService, org.mockito.Mockito.never())
                .uploadToKey(any(), any(), eq("cards/" + TASK_ID + "/1.png"));
    }

    @Test
    @DisplayName("正式生成使用基础 key，预览 key 不含 preview 段")
    void generate_usesBaseCosKey() {
        List<PagePlan> pages = threePages();
        when(planner.plan(any(), any(), any(), any())).thenReturn(pages);
        when(templateEngine.render(any(), any())).thenReturn(List.of("<h1>1</h1>", "<h1>2</h1>", "<h1>3</h1>"));
        when(renderPipeline.render(any(), eq(TASK_ID)))
                .thenReturn(List.of(okResult(), okResult(), okResult()));
        when(complianceChecker.textCheck(any(), any(), any())).thenReturn(
                com.example.aipassagecreator.card.model.ComplianceReport.builder().passed(true).build());
        when(cardPageMapper.deleteByQuery(any())).thenReturn(1);
        when(cardPageMapper.insert(any())).thenReturn(1);
        when(cosService.uploadToKey(any(), any(), any())).thenReturn("uploaded");
        when(cosService.generatePresignedUrl(any())).thenReturn("http://presigned/base.png");

        cardService.generate("正文内容", "标题", "副标题", null, "warm", TASK_ID, "default");

        verify(cosService, times(1)).uploadToKey(any(), any(), eq("cards/" + TASK_ID + "/1.png"));
        verify(cosService, times(1)).uploadToKey(any(), any(), eq("cards/" + TASK_ID + "/2.png"));
        verify(cosService, times(1)).uploadToKey(any(), any(), eq("cards/" + TASK_ID + "/3.png"));
        verify(cosService, org.mockito.Mockito.never())
                .uploadToKey(any(), any(), org.mockito.ArgumentMatchers.contains("preview/"));
    }
}
