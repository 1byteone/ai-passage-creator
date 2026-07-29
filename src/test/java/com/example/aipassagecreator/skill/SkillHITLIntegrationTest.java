package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

/**
 * HITL 全链路集成测试
 * <p>
 * 使用合成图（无 LLM）测试 SkillExecution → SkillExecutionService →
 * SkillExecutionRegistry → SkillConfirmationReaper 的完整集成链路。
 * 合成图模拟 research 的结构：两个阶段，第二个阶段 requireConfirmation=true。
 */
@SpringBootTest
@SqlGroup({
        @Sql(scripts = "classpath:skills/test-skill-execution-schema.sql",
                executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:skills/cleanup-skill-execution.sql",
                executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SkillHITLIntegrationTest {

    @Autowired
    private SkillExecutionMapper skillExecutionMapper;

    @Autowired
    private SkillExecutionRegistry executionRegistry;

    @Autowired
    private SkillExecutionService skillExecutionService;

    @Autowired
    private SkillConfirmationReaper reaper;

    /** 合成图各节点的执行计数器 */
    private final AtomicInteger firstCounter = new AtomicInteger();
    private final AtomicInteger secondCounter = new AtomicInteger();
    private final Map<String, Object> firstOutput = Map.of("firstOutput", "phase1-results");
    private final Map<String, Object> secondOutput = Map.of("secondOutput", "phase2-results");

    private CompiledGraph buildGraph() throws Exception {
        KeyStrategyFactory factory = () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("firstOutput", new ReplaceStrategy());
            strategies.put("secondOutput", new ReplaceStrategy());
            return strategies;
        };
        StateGraph graph = new StateGraph(factory);
        graph.addNode("first", node_async(state -> {
            firstCounter.incrementAndGet();
            return firstOutput;
        }));
        graph.addNode("second", node_async(state -> {
            secondCounter.incrementAndGet();
            // 读取上一阶段产出（可能已被用户修改）
            String upstream = state.value("firstOutput").map(Object::toString).orElse(null);
            return Map.of("secondOutput", "consumed:" + upstream);
        }));
        graph.addEdge(START, "first");
        graph.addEdge("first", "second");
        graph.addEdge("second", END);
        return graph.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
                .interruptsBefore(List.of("second"))
                .build());
    }

    private SkillDefinition buildDefinition() {
        PhaseDefinition phase1 = new PhaseDefinition();
        phase1.setName("first");
        phase1.setOutputKey("firstOutput");
        phase1.setPhaseIndex(1);
        phase1.setRequireConfirmation(false);

        PhaseDefinition phase2 = new PhaseDefinition();
        phase2.setName("second");
        phase2.setOutputKey("secondOutput");
        phase2.setPhaseIndex(2);
        phase2.setRequireConfirmation(true);

        SkillDefinition def = new SkillDefinition();
        def.setName("test-hitl");
        def.setPhases(List.of(phase1, phase2));
        return def;
    }

    @BeforeEach
    @AfterEach
    void resetCounters() {
        firstCounter.set(0);
        secondCounter.set(0);
    }

    @Test
    @DisplayName("完整链路：execute → 暂停 → confirm approve → resume → SUCCESS")
    void fullHITLApproveChain() throws Exception {
        CompiledGraph graph = buildGraph();
        SkillDefinition def = buildDefinition();

        SkillExecution execution = new SkillExecution("test-hitl-1", def,
                Map.of("input", "data"), graph, null, skillExecutionMapper);
        execution.prepare(999L);
        executionRegistry.register(execution, 999L);

        // === Phase 1: 执行到暂停 ===
        CountDownLatch pauseLatch = new CountDownLatch(1);
        Thread execThread = new Thread(() -> {
            execution.execute(event -> {
                if (event.contains("awaiting_confirmation")) {
                    pauseLatch.countDown();
                }
            }, 999L);
        });
        execThread.setDaemon(true);
        execThread.start();
        assertTrue(pauseLatch.await(10, TimeUnit.SECONDS), "应在 10 秒内暂停");

        // 验证暂停状态
        assertTrue(execution.isAwaitingConfirmation());
        assertEquals("second", execution.getPendingPhase());
        assertEquals(1, firstCounter.get(), "first 应已执行");
        assertEquals(0, secondCounter.get(), "second 不应执行");

        // 验证 DB 状态
        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", "test-hitl-1"));
        assertEquals(SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue(), po.getStatus());

        // === Phase 2: approve 后续跑 ===
        CountDownLatch completeLatch = new CountDownLatch(1);
        Thread resumeThread = new Thread(() -> {
            execution.resume(null, event -> {
                if (event.contains("skill.complete")) {
                    completeLatch.countDown();
                }
            });
        });
        resumeThread.setDaemon(true);
        resumeThread.start();
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS), "续跑应在 10 秒内完成");

        // 验证最终状态
        assertEquals(SkillExecutionStatusEnum.SUCCESS.getValue(), execution.getStatus());
        assertEquals(1, firstCounter.get(), "first 不应被重复执行");
        assertEquals(1, secondCounter.get(), "second 应恰好执行一次");
        assertEquals("consumed:phase1-results",
                execution.getContextSharedData().get("secondOutput"));

        // 验证 DB
        po = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", "test-hitl-1"));
        assertEquals(SkillExecutionStatusEnum.SUCCESS.getValue(), po.getStatus());
        assertNotNull(po.getOutputData());

        execThread.interrupt();
        resumeThread.interrupt();
        executionRegistry.remove("test-hitl-1");
    }

    @Test
    @DisplayName("modify 路径：用户修改数据后进入后续阶段")
    void fullHITLModifyChain() throws Exception {
        CompiledGraph graph = buildGraph();
        SkillDefinition def = buildDefinition();

        SkillExecution execution = new SkillExecution("test-hitl-2", def,
                Map.of("input", "data"), graph, null, skillExecutionMapper);
        execution.prepare(999L);
        executionRegistry.register(execution, 999L);

        // 执行到暂停
        CountDownLatch pauseLatch = new CountDownLatch(1);
        Thread execThread = new Thread(() -> {
            execution.execute(event -> {
                if (event.contains("awaiting_confirmation")) {
                    pauseLatch.countDown();
                }
            }, 999L);
        });
        execThread.setDaemon(true);
        execThread.start();
        assertTrue(pauseLatch.await(10, TimeUnit.SECONDS));

        // modify：修改 firstOutput
        CountDownLatch completeLatch = new CountDownLatch(1);
        Thread resumeThread = new Thread(() -> {
            execution.resume(Map.of("firstOutput", "user-edited"), event -> {
                if (event.contains("skill.complete")) {
                    completeLatch.countDown();
                }
            });
        });
        resumeThread.setDaemon(true);
        resumeThread.start();
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS));

        // second 消费的是用户修改后的值
        assertEquals("consumed:user-edited",
                execution.getContextSharedData().get("secondOutput"));

        execThread.interrupt();
        resumeThread.interrupt();
        executionRegistry.remove("test-hitl-2");
    }

    @Test
    @DisplayName("超时收割：条件更新防止与 confirm 双重处理")
    void reaperAtomicClaim() throws Exception {
        CompiledGraph graph = buildGraph();
        SkillDefinition def = buildDefinition();

        SkillExecution execution = new SkillExecution("test-hitl-3", def,
                Map.of("input", "data"), graph, null, skillExecutionMapper);
        execution.prepare(999L);
        executionRegistry.register(execution, 999L);

        // 模拟进入 AWAITING_CONFIRMATION
        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", "test-hitl-3"));
        po.setStatus(SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue());
        po.setPhase("second");
        skillExecutionMapper.update(po);

        // 模拟 confirm 先抢占为 RUNNING（以 DB 级别条件更新）
        SkillExecutionPo claim = new SkillExecutionPo();
        claim.setStatus(SkillExecutionStatusEnum.RUNNING.getValue());
        int confirmed = skillExecutionMapper.updateByQuery(claim, QueryWrapper.create()
                .eq("skill_execution_id", "test-hitl-3")
                .eq("status", SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue()));
        assertEquals(1, confirmed, "confirm 应成功抢占");

        // reaper 再抢：不应命中
        SkillExecutionPo reaperClaim = new SkillExecutionPo();
        reaperClaim.setStatus(SkillExecutionStatusEnum.FAILED.getValue());
        reaperClaim.setErrorMessage("等待确认超时");
        int reaped = skillExecutionMapper.updateByQuery(reaperClaim, QueryWrapper.create()
                .eq("skill_execution_id", "test-hitl-3")
                .eq("status", SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue()));
        assertEquals(0, reaped, "reaper 不应命中已被抢占的执行");

        // 验证 DB 仍为 RUNNING
        SkillExecutionPo after = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", "test-hitl-3"));
        assertEquals(SkillExecutionStatusEnum.RUNNING.getValue(), after.getStatus());

        executionRegistry.remove("test-hitl-3");
    }

    @Test
    @DisplayName("非法操作：未暂停时 resume 被拒绝")
    void resumeRejectedWhenNotPaused() throws Exception {
        CompiledGraph graph = buildGraph();
        SkillDefinition def = buildDefinition();
        SkillExecution execution = new SkillExecution("test-hitl-4", def,
                Map.of("input", "data"), graph, null, skillExecutionMapper);

        // 尚未执行，直接 resume 应拒绝
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> execution.resume(null, event -> {
                }));
        assertTrue(ex.getMessage().contains("不允许续跑"));
        assertEquals(SkillExecutionStatusEnum.PENDING.getValue(), execution.getStatus(),
                "失败时状态不应被污染");
    }

    @Test
    @DisplayName("终点确认：单阶段图无中断点，一次跑完")
    void singlePhaseGraphRunsToCompletion() throws Exception {
        KeyStrategyFactory factory = () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("out", new ReplaceStrategy());
            return strategies;
        };
        StateGraph graph = new StateGraph(factory);
        graph.addNode("only", node_async(state -> Map.of("out", "done")));
        graph.addEdge(START, "only");
        graph.addEdge("only", END);
        CompiledGraph compiled = graph.compile();

        PhaseDefinition phase = new PhaseDefinition();
        phase.setName("only");
        phase.setOutputKey("out");
        phase.setPhaseIndex(1);

        SkillDefinition def = new SkillDefinition();
        def.setName("test-single");
        def.setPhases(List.of(phase));

        SkillExecution execution = new SkillExecution("test-hitl-5", def,
                Map.of(), compiled, null, skillExecutionMapper);
        execution.prepare(999L);

        CountDownLatch completeLatch = new CountDownLatch(1);
        Thread execThread = new Thread(() -> {
            execution.execute(event -> {
                if (event.contains("skill.complete")) {
                    completeLatch.countDown();
                }
            }, 999L);
        });
        execThread.setDaemon(true);
        execThread.start();
        assertTrue(completeLatch.await(5, TimeUnit.SECONDS), "无中断图应在 5 秒内跑完");

        assertEquals(SkillExecutionStatusEnum.SUCCESS.getValue(), execution.getStatus());
        assertFalse(execution.isAwaitingConfirmation());

        execThread.interrupt();
    }
}