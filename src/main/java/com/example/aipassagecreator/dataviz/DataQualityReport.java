package com.example.aipassagecreator.dataviz;

import java.util.List;

/** 数据质量画像：含中文警告，随报告展示 */
public record DataQualityReport(
        int rowCount,
        List<FieldProfile> fields,
        int missingCells,
        int duplicateRows,
        List<String> warnings) {
}
