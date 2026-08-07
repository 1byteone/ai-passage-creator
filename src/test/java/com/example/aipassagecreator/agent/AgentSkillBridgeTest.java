package com.example.aipassagecreator.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentSkillBridgeTest {

    @Test
    void translate_skillProgress_toAgentPhase() {
        String skillEvent = "{\"type\":\"skill.progress\",\"skillExecutionId\":\"ex1\","
                + "\"skillName\":\"research\",\"phase\":\"搜索资料\",\"phaseIndex\":1,"
                + "\"totalPhases\":3,\"data\":\"40%\"}";
        String agentEvent = AgentSkillEventTranslator.translate(skillEvent);
        assertTrue(agentEvent.contains("agent.skill_phase"));
        assertTrue(agentEvent.contains("\"phase\":1"));
        assertTrue(agentEvent.contains("\"name\":\"搜索资料\""));
    }

    @Test
    void translate_awaitingConfirmation_toSkillConfirm() {
        String skillEvent = "{\"type\":\"skill.awaiting_confirmation\",\"skillExecutionId\":\"ex1\","
                + "\"skillName\":\"research\",\"phase\":\"搜索资料\",\"phaseIndex\":1,\"totalPhases\":3}";
        String agentEvent = AgentSkillEventTranslator.translate(skillEvent);
        assertTrue(agentEvent.contains("agent.skill_confirm"));
        assertTrue(agentEvent.contains("\"executionId\":\"ex1\""));
    }

    @Test
    void translate_started_toSkillStarted() {
        String skillEvent = "{\"type\":\"skill.started\",\"skillExecutionId\":\"ex1\","
                + "\"skillName\":\"research\",\"totalPhases\":3}";
        assertTrue(AgentSkillEventTranslator.translate(skillEvent).contains("agent.skill_started"));
    }

    @Test
    void translate_complete_noAgentSkillEvent() {
        // skill.complete 不再桥接为 skill_*（最终文本由 Agent 汇总落库），返回空
        String skillEvent = "{\"type\":\"skill.complete\",\"skillExecutionId\":\"ex1\","
                + "\"skillName\":\"research\",\"status\":\"SUCCESS\",\"outputData\":{}}";
        assertEquals("", AgentSkillEventTranslator.translate(skillEvent));
    }
}
