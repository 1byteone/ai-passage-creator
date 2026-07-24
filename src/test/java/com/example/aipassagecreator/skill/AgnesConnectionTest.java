package com.example.aipassagecreator.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TESTS", matches = "true")
public class AgnesConnectionTest {

    @Autowired
    @Qualifier("agnesChatModel")
    private ChatModel agnesChatModel;

    @Test
    void testAgnesConnection() {
        String response = agnesChatModel.call(
                new Prompt(new UserMessage("Hello, respond with just 'OK'.")))
                .getResult().getOutput().getText();
        assertNotNull(response);
        System.out.println("AGNES 响应: " + response);
    }
}
