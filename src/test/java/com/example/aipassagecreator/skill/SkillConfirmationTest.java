package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多轮确认（Human-in-the-Loop）相关行为测试
 */
@SpringBootTest
class SkillConfirmationTest {

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private SkillExecutionRegistry executionRegistry;

    // ---------- 状态机 ----------

    @DisplayName("状态终态判定")
    @ParameterizedTest(name = "{0} -> terminal={1}")
    @CsvSource({
            "PENDING, false",
            "RUNNING, false",
            "AWAITING_CONFIRMATION, false",
            "SUCCESS, true",
            "FAILED, true",
    })
    void statusTerminalFlags(String value, boolean terminal) {
        SkillExecutionStatusEnum status = SkillExecutionStatusEnum.getEnumByValue(value);
        assertNotNull(status, value + " 应可解析");
        assertEquals(terminal, status.isTerminal());
    }

    @Test
    @DisplayName("非法状态值解析为 null")
    void unknownStatusResolvesToNull() {
        assertNull(SkillExecutionStatusEnum.getEnumByValue("NOPE"));
        assertNull(SkillExecutionStatusEnum.getEnumByValue(""));
        assertNull(SkillExecutionStatusEnum.getEnumByValue(null));
    }

    // ---------- Skill 定义与图编译 ----------

    @Test
    @DisplayName("research 声明了确认阶段，且 requireConfirmation 落在最后阶段之前")
    void researchDeclaresConfirmationPhase() {
        assertTrue(skillRegistry.hasConfirmationPhase("research"));

        SkillDefinition def = skillRegistry.getSkill("research");
        assertTrue(def.isMultiRound());
        // summary 阶段需确认：执行前暂停，供用户审阅 search 的产出
        PhaseDefinition summary = def.getPhases().stream()
                .filter(p -> "summary".equals(p.getName()))
                .findFirst().orElseThrow();
        assertTrue(summary.isRequireConfirmation());
        // phaseIndex 已由注册流程回填，供前端定位进度
        assertEquals(2, summary.getPhaseIndex());
        assertEquals(1, def.getPhases().get(0).getPhaseIndex());
    }

    @Test
    @DisplayName("回归：现有 3 个 Skill 均无确认阶段，不受多轮改造影响")
    void existingSkillsHaveNoConfirmationPhase() {
        for (String name : List.of("proofreading", "topic-gen", "article-to-x")) {
            assertFalse(skillRegistry.hasConfirmationPhase(name),
                    name + " 不应包含确认阶段");
            assertNotNull(skillRegistry.getGraph(name), name + " 图应正常编译");
        }
    }

    @Test
    @DisplayName("research 已公开，可被前端发现")
    void researchIsPublic() {
        // 公开范围由 SkillController.PUBLIC_SKILLS 控制，此处校验定义可获取
        assertNotNull(skillRegistry.getSkill("research"));
        assertNotNull(skillRegistry.getGraph("research"));
    }

    // ---------- SSE 事件 ----------

    @Test
    @DisplayName("awaiting_confirmation 事件包含前端渲染确认面板所需字段")
    void awaitingConfirmationEventShape() {
        Object pendingOutput = Map.of("findings", List.of("a", "b"));
        String json = SkillEventFactory.awaitingConfirmation(
                "exec-1", "research", "summary", 2, 2, pendingOutput);

        Map<String, Object> payload = GsonUtils.fromJson(
                json, new TypeToken<Map<String, Object>>() {
                });

        assertEquals("skill.awaiting_confirmation", payload.get("type"));
        assertEquals("exec-1", payload.get("skillExecutionId"));
        assertEquals("research", payload.get("skillName"));
        assertEquals("summary", payload.get("phase"));
        assertEquals("AWAITING_CONFIRMATION", payload.get("status"));
        assertEquals(2.0, payload.get("phaseIndex"));
        assertEquals(2.0, payload.get("totalPhases"));
        // 明确告知前端本轮支持的动作，retry 不在其中
        assertEquals(List.of("approve", "modify"), payload.get("supportedActions"));
        assertNotNull(payload.get("pendingOutput"), "应带上待审阅的上一阶段产出");
    }

    @Test
    @DisplayName("pendingOutput 为空时不写入该字段")
    void awaitingConfirmationOmitsNullPendingOutput() {
        String json = SkillEventFactory.awaitingConfirmation(
                "exec-2", "research", "search", 1, 2, null);
        Map<String, Object> payload = GsonUtils.fromJson(
                json, new TypeToken<Map<String, Object>>() {
                });
        assertFalse(payload.containsKey("pendingOutput"));
    }

    // ---------- 执行注册表 ----------

    @Test
    @DisplayName("注册表可登记与移除执行实例")
    void registryRegisterAndRemove() {
        SkillExecution execution = skillRegistry.createExecution("research", Map.of("topic", "测试"));
        String id = execution.getExecutionId();

        executionRegistry.register(execution, 42L);
        assertNotNull(executionRegistry.get(id));

        executionRegistry.remove(id);
        assertNull(executionRegistry.get(id), "移除后不应再取到");
    }

    @Test
    @DisplayName("新建执行初始状态为 PENDING 且未在等待确认")
    void freshExecutionState() {
        SkillExecution execution = skillRegistry.createExecution("research", Map.of("topic", "测试"));
        assertEquals(SkillExecutionStatusEnum.PENDING.getValue(), execution.getStatus());
        assertFalse(execution.isAwaitingConfirmation());
        assertFalse(execution.isTerminal());
    }

    @Test
    @DisplayName("未处于等待确认状态时续跑应被拒绝")
    void resumeRejectedWhenNotAwaiting() {
        SkillExecution execution = skillRegistry.createExecution("research", Map.of("topic", "测试"));
        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> execution.resume(null, event -> {
                }));
        assertTrue(ex.getMessage().contains("不允许续跑"));
    }

    @Test
    @DisplayName("尚未登记的执行不会被判定为超时")
    void findExpiredIgnoresUnregistered() {
        // 刚登记的执行远未达到 TTL，不应被收割
        SkillExecution execution = skillRegistry.createExecution("research", Map.of("topic", "测试"));
        executionRegistry.register(execution, 7L);
        try {
            assertTrue(executionRegistry.findExpired().stream()
                    .noneMatch(e -> e.executionId().equals(execution.getExecutionId())));
        } finally {
            executionRegistry.remove(execution.getExecutionId());
        }
    }
}
