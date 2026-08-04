package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.handwriting.model.HandwritingExportVO;
import com.example.aipassagecreator.handwriting.model.HandwritingFont;
import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 手写编辑器 API。
 * 提供字体列表、纸张列表、预览、PNG/PDF 导出等端点。
 */
@Slf4j
@RestController
@RequestMapping("/handwriting")
@RequiredArgsConstructor
public class HandwritingController {

    private final HandwritingFontManager fontManager;
    private final HandwritingService handwritingService;
    private final HandwritingAsyncService asyncService;

    private static final List<Map<String, String>> PAPER_TYPES = List.of(
            Map.of("key", "blank", "name", "空白纸"),
            Map.of("key", "line", "name", "横线纸"),
            Map.of("key", "grid", "name", "方格纸"),
            Map.of("key", "tianzi", "name", "田字格"),
            Map.of("key", "dot", "name", "点阵纸")
    );

    @GetMapping("/fonts")
    @Operation(summary = "获取可用手写字体列表")
    public BaseResponse<List<HandwritingFont>> listFonts() {
        return ResultUtils.success(fontManager.listFonts());
    }

    @GetMapping("/papers")
    @Operation(summary = "获取纸张类型列表")
    public BaseResponse<List<Map<String, String>>> listPapers() {
        return ResultUtils.success(PAPER_TYPES);
    }

    @PostMapping("/preview")
    @Operation(summary = "手写效果预览（同步，不扣配额）")
    @RateLimit(limit = 10, window = 60, key = "handwriting_preview")
    public BaseResponse<?> preview(@Valid @RequestBody HandwritingRequest request) {
        ThrowUtils.throwIf(request == null || request.content().isBlank(),
                ErrorCode.PARAMS_ERROR, "内容不能为空");
        try {
            String url = handwritingService.preview(request);
            return ResultUtils.success(url);
        } catch (Exception e) {
            log.error("手写预览失败", e);
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "预览失败: " + e.getMessage());
        }
    }

    @PostMapping("/export/png")
    @Operation(summary = "PNG 导出（异步，扣配额，SSE 推送）")
    @RateLimit(limit = 3, window = 60, key = "handwriting_export")
    public BaseResponse<HandwritingExportVO> exportPng(
            @Valid @RequestBody HandwritingRequest request) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        asyncService.export(request, taskId, null);
        return ResultUtils.success(new HandwritingExportVO(taskId,
                "/api/handwriting/progress/" + taskId));
    }

    @PostMapping("/export/pdf")
    @Operation(summary = "PDF 导出（异步，扣配额，SSE 推送）")
    @RateLimit(limit = 3, window = 60, key = "handwriting_export")
    public BaseResponse<HandwritingExportVO> exportPdf(
            @Valid @RequestBody HandwritingRequest request) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        asyncService.export(request, taskId, null);
        return ResultUtils.success(new HandwritingExportVO(taskId,
                "/api/handwriting/progress/" + taskId));
    }
}
