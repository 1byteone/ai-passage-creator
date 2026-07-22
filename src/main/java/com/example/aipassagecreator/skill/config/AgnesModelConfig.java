package com.example.aipassagecreator.skill.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.example.aipassagecreator.skill.ModelRouter;
import com.example.aipassagecreator.skill.ModelRouterConfig;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgnesModelConfig {

    /**
     * AGNES ChatModel（通过 OpenAI 兼容接口接入）
     * Spring AI 的 OpenAiAutoConfiguration 已自动从 application.yml 读取配置，
     * 这里显式声明 Bean 以便注入 ModelRouter
     */
    @Bean
    public ChatModel agnesChatModel(OpenAiConnectionProperties connectionProperties) {
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey(connectionProperties.getApiKey())
                        .baseUrl(connectionProperties.getBaseUrl())
                        .build())
                .build();
    }

    /**
     * ModelRouter 路由策略
     */
    @Bean
    public ModelRouter modelRouter(ChatModel agnesChatModel,
                                    DashScopeChatModel dashscopeChatModel,
                                    ModelRouterConfig config) {
        return new ModelRouter(agnesChatModel, dashscopeChatModel, config);
    }
}