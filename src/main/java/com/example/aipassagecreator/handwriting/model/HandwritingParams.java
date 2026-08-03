package com.example.aipassagecreator.handwriting.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * 手写扰动参数 — 控制手写效果的自然程度。
 */
public record HandwritingParams(
    @DecimalMin("0.0") @DecimalMax("10.0")
    double positionJitter,   // 位置随机偏移 (px), 默认 2.0

    @DecimalMin("0.0") @DecimalMax("5.0")
    double rotationJitter,   // 旋转随机角度 (°), 默认 1.5

    @DecimalMin("0.0") @DecimalMax("20.0")
    double sizeJitter,       // 字号随机变化 (%), 默认 5.0

    @DecimalMin("0.0") @DecimalMax("1.0")
    double inkDensity        // 墨迹浓淡 (0.0-1.0), 默认 0.85
) {
    public static HandwritingParams defaults() {
        return new HandwritingParams(2.0, 1.5, 5.0, 0.85);
    }
}
