package com.example.aipassagecreator.dataviz;

import java.util.List;
import java.util.Map;

/** 解析后的数据集：值统一为字符串，类型推断由 DatasetValidator 负责 */
public record Dataset(List<Map<String, String>> rows, List<String> headers) {
    public Dataset {
        rows = List.copyOf(rows);
        headers = List.copyOf(headers);
    }
}
