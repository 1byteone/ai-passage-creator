package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillNodeActionTest {

    @Test
    void passesStructuredPhaseOutputToReferencedVariable() {
        PhaseDefinition phase = new PhaseDefinition();
        PhaseDefinition.VariableRef variable = new PhaseDefinition.VariableRef();
        variable.setName("reviewResult");
        variable.setRef("content_review");
        phase.setVariables(List.of(variable));

        SkillNodeAction action = new SkillNodeAction(
                phase,
                3,
                3,
                null,
                null,
                null,
                Map.of("content_review", "reviewResult"),
                List.of(),
                null
        );
        Map<String, Object> reviewResult = Map.of(
                "overallScore", 88,
                "summary", "结构清晰"
        );
        OverAllState state = new OverAllState(Map.of("reviewResult", reviewResult));

        assertEquals(reviewResult, action.resolveInputs(state).get("reviewResult"));
    }
}
