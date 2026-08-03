package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 手写导出异步服务。
 * 通过 handwriting_ 前缀的 SSE key 推送导出进度和结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandwritingAsyncService {

    private final HandwritingService handwritingService;
    private final SseEmitterManager sseEmitterManager;

    /** SSE key 前缀隔离 */
    private static final String SSE_PREFIX = "handwriting_";

    @Async("cardExecutor")
    public void export(HandwritingRequest request, String taskId, String title) {
        String sseKey = SSE_PREFIX + taskId;
        try {
            sseEmitterManager.send(sseKey,
                    GsonUtils.toJson(Map.of("type", "handwriting_progress",
                            "message", "开始渲染...")));

            List<String> urls = handwritingService.export(request, taskId, title);

            sseEmitterManager.send(sseKey,
                    GsonUtils.toJson(Map.of(
                            "type", "handwriting_complete",
                            "taskId", taskId,
                            "urls", urls,
                            "pageCount", urls.size())));
            sseEmitterManager.complete(sseKey);
            log.info("手写导出完成: taskId={}, pages={}", taskId, urls.size());
        } catch (Exception e) {
            log.error("手写导出失败: taskId={}", taskId, e);
            String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
            sseEmitterManager.send(sseKey,
                    GsonUtils.toJson(Map.of("type", "handwriting_error", "error", msg)));
            sseEmitterManager.complete(sseKey);
        }
    }
}
