package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.service.RagAugmentationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagAugmentChatTest {

    /** 空结果不阻断（软参考语义） */
    @Test
    void emptyResult_isEmpty_true() {
        assertTrue(RagAugmentationService.AugmentedResult.EMPTY.isEmpty());
    }

    @Test
    void sourceRef_toEventJson_containsExpectedFields() {
        String json = AgentEventFactory.ragReference("req", List.of(
                new AgentEventFactory.SourceRef("r1", "我的文章", "article", "2026-08-01")));
        assertTrue(json.contains("agent.rag_reference"));
        assertTrue(json.contains("\"refId\":\"r1\""));
        assertTrue(json.contains("\"title\":\"我的文章\""));
    }
}
