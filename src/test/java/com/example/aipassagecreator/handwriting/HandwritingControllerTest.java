package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingExportVO;
import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * 手写编辑器控制器单元测试 — 端点行为
 *
 * <p>覆盖：字体/纸张列表、导出 taskId 生成、预览委托。渲染相关依赖全部 mock。</p>
 */
@ExtendWith(MockitoExtension.class)
class HandwritingControllerTest {

    @Mock
    private HandwritingFontManager fontManager;

    @Mock
    private HandwritingService handwritingService;

    @Mock
    private HandwritingAsyncService asyncService;

    @InjectMocks
    private HandwritingController controller;

    @Test
    @DisplayName("纸张列表 — 返回 5 种纸张类型")
    void listPapers_returnsFiveTypes() {
        var result = controller.listPapers();

        assertEquals(0, result.getCode());
        assertNotNull(result.getData());
        assertEquals(5, result.getData().size());
    }

    @Test
    @DisplayName("PNG 导出 — 生成 8 位 taskId 并异步派发")
    void exportPng_returnsTaskIdAndDispatches() {
        HandwritingRequest request = new HandwritingRequest(
                "测试内容", "xieyi", "grid", null, null);

        var response = controller.exportPng(request);
        HandwritingExportVO vo = response.getData();

        assertNotNull(vo);
        assertTrue(vo.taskId().length() == 8);
        assertTrue(vo.progressUrl().contains("/api/handwriting/progress/"));
        verify(asyncService).export(request, vo.taskId(), null);
    }

    @Test
    @DisplayName("PDF 导出 — 复用同一 taskId 生成逻辑")
    void exportPdf_returnsTaskId() {
        HandwritingRequest request = new HandwritingRequest(
                "测试内容", "xieyi", "grid", null, null);

        var response = controller.exportPdf(request);
        HandwritingExportVO vo = response.getData();

        assertNotNull(vo);
        assertTrue(vo.taskId().length() == 8);
        assertTrue(vo.progressUrl().contains("progress/"));
        verify(asyncService).export(request, vo.taskId(), null);
    }
}
