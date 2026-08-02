package com.example.aipassagecreator.publish.platform;

/**
 * 合规检查结果项。
 */
public record ComplianceIssue(
        String rule,
        String level,   // ERROR / WARNING
        String message
) {}
