package com.example.aipassagecreator.publish.platform;

import java.util.List;

/**
 * 平台内容适配器接口。按平台格式转换文章内容，并校验合规性。
 * <p>适配器由 Spring 自动发现（@Component），PlatformAdapterRegistry 按 {@link #platform()} 汇总。</p>
 */
public interface PlatformAdapter {

    /** 平台标识（与 methodology name / publish_schedule.platform 一致） */
    String platform();

    /** Markdown 正文 → 平台格式 */
    PlatformContent convert(String title, String markdown,
                            com.example.aipassagecreator.methodology.MethodologyDefinition def);

    /** 平台规则校验 */
    List<ComplianceIssue> validate(PlatformContent content,
                                   com.example.aipassagecreator.methodology.MethodologyDefinition def);
}
