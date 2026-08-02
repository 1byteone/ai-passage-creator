package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HITL 检查点重启续跑集成测试
 * <p>
 * 证明核心价值：执行暂停于 AWAITING_CONFIRMATION 后，进程内状态全部清除
 * （注册表 + SkillContext，模拟应用重启），仅凭 DB 检查点 + skill_execution 行
 * 重建实例后仍可从检查点续跑成功，且已完成阶段不重复执行。
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Sql(scripts = {
        "classpath:skills/test-skill-execution-schema.sql",
        "classpath:skills/cleanup-skill-tables.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:skills/cleanup-skill-tables.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SkillExecutionRestartIntegrationTest {

    @Autowired
    private PortableCheckpointSaver checkpointSaver;

    @Autowired
    private SkillExecutionMapper skillExecutionMapper;

    @Autowired
    private SkillExecutionRegistry executionRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AtomicInteger firstCounter = new AtomicInteger();
    private final AtomicInteger secondCounter = new AtomicInteger();
    private final Map<String, Object> firstOutput = Map.of("firstOutput", "phase1-results");

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
            String upstream = state.value("firstOutput").map(Object::toString).orElse(null);
            return Map.of("secondOutput", "consumed:" + upstream);
        }));
        graph.addEdge(START, "first");
        graph.addEdge("first", "second");
        graph.addEdge("second", END);
        // 与生产一致：检查点由 PortableCheckpointSaver 落库
        return graph.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(checkpointSaver).build())
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
        def.setName("test-restart");
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
    @DisplayName("重启续跑：清空内存状态后仅凭 DB 检查点重建实例并续跑成功")
    void restart_resumeFromDbCheckpoint() throws Exception {
        CompiledGraph graph = buildGraph();
        SkillDefinition def = buildDefinition();
        String executionId = "restart-1";

        SkillExecution execution = new SkillExecution(executionId, def,
                Map.of("input", "data"), graph, null, skillExecutionMapper);
        execution.prepare(999L);
        executionRegistry.register(execution, 999L);

        // === 阶段 1: 执行到暂停 ===
        CountDownLatch pauseLatch = new CountDownLatch(1);
        Thread execThread = new Thread(() -> execution.execute(event -> {
            if (event.contains("awaiting_confirmation")) {
                pauseLatch.countDown();
            }
        }, 999L));
        execThread.setDaemon(true);
        execThread.start();
        assertTrue(pauseLatch.await(10, TimeUnit.SECONDS), "应在 10 秒内暂停");
        execThread.join(2000);

        assertTrue(execution.isAwaitingConfirmation());
        assertEquals(1, firstCounter.get(), "first 应已执行一次");

        // 检查点已落库（threadId = executionId）
        Integer checkpointRows = jdbcTemplate.queryForObject(
                "select count(*) from skill_checkpoint where thread_id = ?", Integer.class, executionId);
        assertTrue(checkpointRows != null && checkpointRows > 0, "检查点应已持久化到 DB");

        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", executionId));
        assertEquals(SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue(), po.getStatus());

        // === 阶段 2: 模拟应用重启 —— 清空进程内全部状态 ===
        executionRegistry.remove(executionId);
        SkillContext.remove(executionId);

        // === 阶段 3: 仅凭 DB 记录重建实例并续跑 ===
        SkillExecution rebuilt = new SkillExecution(executionId, def,
                Map.of("input", "data"), graph, null, skillExecutionMapper);
        rebuilt.restoreFrom(po);

        CountDownLatch completeLatch = new CountDownLatch(1);
        Thread resumeThread = new Thread(() -> rebuilt.resume(null, event -> {
            if (event.contains("skill.complete")) {
                completeLatch.countDown();
            }
        }));
        resumeThread.setDaemon(true);
        resumeThread.start();
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS), "重启续跑应在 10 秒内完成");

        // first 不被重复执行（续跑从检查点开始）；second 恰好执行一次
        assertEquals(1, firstCounter.get(), "first 不应被重复执行");
        assertEquals(1, secondCounter.get(), "second 应恰好执行一次");
        assertEquals(SkillExecutionStatusEnum.SUCCESS.getValue(), rebuilt.getStatus());
        assertEquals("consumed:phase1-results", rebuilt.getContextSharedData().get("secondOutput"));

        // DB 落为终态 + 输出
        po = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", executionId));
        assertEquals(SkillExecutionStatusEnum.SUCCESS.getValue(), po.getStatus());
        assertTrue(po.getOutputData() != null && po.getOutputData().contains("consumed:phase1-results"),
                "outputData 应包含续跑阶段产出");

        execThread.interrupt();
        resumeThread.interrupt();
    }
}
