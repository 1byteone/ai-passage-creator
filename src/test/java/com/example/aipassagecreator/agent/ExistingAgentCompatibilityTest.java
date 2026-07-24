package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.agent.agents.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class ExistingAgentCompatibilityTest {

    @Autowired private TitleGeneratorAgent titleGeneratorAgent;
    @Autowired private OutlineGeneratorAgent outlineGeneratorAgent;
    @Autowired private ContentGeneratorAgent contentGeneratorAgent;
    @Autowired private ImageAnalyzerAgent imageAnalyzerAgent;
    @Autowired private ArticleAgentOrchestrator orchestrator;

    @Test
    void testAllAgentsLoaded() {
        assertNotNull(titleGeneratorAgent);
        assertNotNull(outlineGeneratorAgent);
        assertNotNull(contentGeneratorAgent);
        assertNotNull(imageAnalyzerAgent);
        assertNotNull(orchestrator);
    }
}