package com.example.aipassagecreator.dataviz;

/** 单数值列统计量；changeRate = (last-first)/|first|，first=0 时为 0 */
public record DatasetStats(
        double min, double max, double sum, double avg,
        double first, double last, double changeRate, int validCount) {
}
