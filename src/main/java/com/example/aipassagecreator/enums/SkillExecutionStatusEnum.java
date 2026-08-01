package com.example.aipassagecreator.enums;

import lombok.Getter;

/**
 * Skill 执行状态
 * <p>
 * 状态流转：
 * <pre>
 * PENDING → RUNNING → SUCCESS
 *                  ↘ AWAITING_CONFIRMATION → RUNNING → SUCCESS
 *                  ↘ FAILED（含超时未确认）
 * </pre>
 */
@Getter
public enum SkillExecutionStatusEnum {

    /** 已创建，尚未开始执行 */
    PENDING("PENDING"),
    /** 执行中 */
    RUNNING("RUNNING"),
    /** 已暂停，等待用户确认后续跑 */
    AWAITING_CONFIRMATION("AWAITING_CONFIRMATION"),
    /** 执行成功 */
    SUCCESS("SUCCESS"),
    /** 执行失败（含超时未确认被收割） */
    FAILED("FAILED");

    private final String value;

    SkillExecutionStatusEnum(String value) {
        this.value = value;
    }

    /**
     * 是否为终态（不会再变化）
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    public static SkillExecutionStatusEnum getEnumByValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SkillExecutionStatusEnum anEnum : values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
