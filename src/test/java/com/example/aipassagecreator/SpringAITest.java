package com.example.aipassagecreator;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@SpringBootTest
@Disabled("需要有效的 DASHSCOPE_API_KEY 和 RUN_LIVE_AI_TESTS=true 环境变量")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TESTS", matches = "true")
public class SpringAITest {

    @Resource
    private DashScopeChatModel chatModel;

    @Test
    public void testChat() {
        // 同步调用
        String response = chatModel.call("你好，请介绍一下你自己");
        System.out.println(response);

        // 流式调用
        Flux<ChatResponse> stream = chatModel.stream(
            new Prompt("用一句话介绍 Spring AI")
        );
        stream.subscribe(chunk ->
            System.out.print(chunk.getResult().getOutput().getText())
        );
    }
}
