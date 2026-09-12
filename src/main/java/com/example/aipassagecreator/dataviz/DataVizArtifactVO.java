package com.example.aipassagecreator.dataviz;

/**
 * 报告产物就绪状态。
 * <p>
 * 产物由 {@link DataVizPostProcessor} 异步生成，查询时可能只有 HTML 没有 PNG
 * （渲染引擎不可用时 PNG 永久缺席），故两项就绪状态分开表达。
 */
public record DataVizArtifactVO(
        boolean htmlReady,
        boolean pngReady,
        String htmlUrl,
        String pngUrl) {
}
