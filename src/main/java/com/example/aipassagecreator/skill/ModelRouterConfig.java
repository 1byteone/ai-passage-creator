package com.example.aipassagecreator.skill;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "model.router")
public class ModelRouterConfig {

    /** 全局默认模型：agnes / dashscope */
    private String defaultModel = "agnes";

    /** Skill 引擎默认模型 */
    private String skillDefault = "agnes";

    /** 文章写作默认模型 */
    private String articleDefault = "dashscope";

    /** 降级模型 */
    private String fallback = "dashscope";

    /** 图片生成模型 */
    private String imageModel = "agnes-image-2.1-flash";
}