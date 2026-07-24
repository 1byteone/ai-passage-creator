package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.model.dto.skill.TopicOption;
import com.example.aipassagecreator.skill.parsers.TopicOptionsOutputParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopicOptionsOutputParserTest {

    @Test
    void parsesStableTopicOptionDto() {
        TopicOptionsOutputParser parser = new TopicOptionsOutputParser();
        List<TopicOption> options = parser.parse("""
                ```json
                [{
                  "title": "AI 写作工作流",
                  "type": "实战教程型",
                  "workload": "中",
                  "outline": ["问题", "步骤"],
                  "pros": ["可复用"],
                  "cons": ["需要案例"]
                }]
                ```
                """, new PhaseDefinition());

        assertEquals(1, options.size());
        assertEquals("AI 写作工作流", options.get(0).getTitle());
        assertEquals(List.of("可复用"), options.get(0).getPros());
    }
}
