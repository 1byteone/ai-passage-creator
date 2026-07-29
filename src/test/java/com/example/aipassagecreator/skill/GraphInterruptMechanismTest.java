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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StateGraph 中断/续跑机制验证
 * <p>
 * 用不依赖 LLM 的合成图，锁定 Skill 多轮确认所依赖的库行为：
 * interruptBefore 会在目标节点执行「之前」停下，检查点保留已完成阶段的产出，
 * 续跑时传 null 输入即可从断点继续。
 * <p>
 * 若升级 spring-ai-alibaba-graph-core 后此测试失败，说明 HITL 契约已变，
 * SkillExecution 的暂停/续跑逻辑需同步调整。
 */
class GraphInterruptMechanismTest {

    private static final String NODE_FIRST = "first";
    private static final String NODE_SECOND = "second";

    /** 记录各节点实际执行次数，用于断言「中断点之后的节点未被执行」 */
    private final Map<String, Integer> executionCounts = new HashMap<>();

    private CompiledGraph buildGraph() throws Exception {
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("firstOutput", new ReplaceStrategy());
            strategies.put("secondOutput", new ReplaceStrategy());
            return strategies;
        };

        StateGraph graph = new StateGraph(keyStrategyFactory);
        graph.addNode(NODE_FIRST, node_async(state -> {
            executionCounts.merge(NODE_FIRST, 1, Integer::sum);
            return Map.of("firstOutput", "generated-by-first");
        }));
        graph.addNode(NODE_SECOND, node_async(state -> {
            executionCounts.merge(NODE_SECOND, 1, Integer::sum);
            // 读取上一阶段（可能已被用户修改）的产出
            String upstream = state.value("firstOutput").map(Object::toString).orElse("<missing>");
            return Map.of("secondOutput", "consumed:" + upstream);
        }));
        graph.addEdge(START, NODE_FIRST);
        graph.addEdge(NODE_FIRST, NODE_SECOND);
        graph.addEdge(NODE_SECOND, END);

        // second 节点执行「之前」中断，模拟 requireConfirmation: true
        return graph.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
                .interruptsBefore(java.util.List.of(NODE_SECOND))
                .build());
    }

    @Test
    @DisplayName("interruptBefore 在目标节点执行前暂停，且后续节点未被执行")
    void interruptBeforePausesAtTargetNode() throws Exception {
        CompiledGraph graph = buildGraph();
        RunnableConfig config = RunnableConfig.builder().threadId("thread-pause").build();

        graph.stream(Map.of(), config).blockLast();

        // first 已执行，second 尚未执行
        assertEquals(1, executionCounts.getOrDefault(NODE_FIRST, 0));
        assertEquals(0, executionCounts.getOrDefault(NODE_SECOND, 0));

        var snapshot = graph.getState(config);
        assertNotNull(snapshot);
        // next() 指向即将执行、被中断拦下的节点
        assertEquals(NODE_SECOND, snapshot.next());
        assertFalse(END.equals(snapshot.next()), "中断时不应已到达 END");
        // 已完成阶段的产出保留在检查点中，可供用户审阅
        assertEquals("generated-by-first", snapshot.state().value("firstOutput").orElse(null));
    }

    @Test
    @DisplayName("approve：用 getState().config() 续跑，已完成阶段不重跑")
    void resumeWithCheckpointConfigCompletesGraph() throws Exception {
        CompiledGraph graph = buildGraph();
        RunnableConfig config = RunnableConfig.builder().threadId("thread-approve").build();

        graph.stream(Map.of(), config).blockLast();
        assertEquals(NODE_SECOND, graph.getState(config).next());

        // 关键：必须用状态快照携带的 config（含 checkPointId）
        RunnableConfig resumeConfig = graph.getState(config).config();
        graph.stream(null, resumeConfig).blockLast();

        assertEquals(1, executionCounts.getOrDefault(NODE_SECOND, 0), "second 应恰好执行一次");
        assertEquals(1, executionCounts.getOrDefault(NODE_FIRST, 0), "first 不应被重复执行");
        assertEquals("consumed:generated-by-first",
                graph.getState(config).state().value("secondOutput").orElse(null));
        // 跑完后 next 指向 END
        assertEquals(END, graph.getState(config).next());
    }

    @Test
    @DisplayName("陷阱：用原 config 续跑会从头重跑已完成阶段，而非从断点继续")
    void resumeWithOriginalConfigRestartsFromBeginning() throws Exception {
        CompiledGraph graph = buildGraph();
        RunnableConfig config = RunnableConfig.builder().threadId("thread-trap").build();

        graph.stream(Map.of(), config).blockLast();
        assertEquals(1, executionCounts.getOrDefault(NODE_FIRST, 0));

        // 原 config 不含 checkPointId：不报错，但会从 START 重新开始，
        // 再次执行 first 并再次在 second 前中断——白烧一次 token 且永远推进不了。
        // SkillExecution.resume() 必须避免这条路径。
        graph.stream(null, config).blockLast();

        assertEquals(2, executionCounts.getOrDefault(NODE_FIRST, 0), "first 被重复执行");
        assertEquals(0, executionCounts.getOrDefault(NODE_SECOND, 0), "second 仍未执行");
        assertEquals(NODE_SECOND, graph.getState(config).next(), "仍停在同一中断点");
    }

    @Test
    @DisplayName("modify：updateState 写入的修改会进入后续阶段的输入")
    void updateStateFeedsModifiedDataIntoNextPhase() throws Exception {
        CompiledGraph graph = buildGraph();
        RunnableConfig config = RunnableConfig.builder().threadId("thread-modify").build();

        graph.stream(Map.of(), config).blockLast();

        // interruptBefore 模式下第三个参数传 null；返回的 config 已含 checkPointId，可直接续跑
        RunnableConfig updated = graph.updateState(
                config, Map.of("firstOutput", "edited-by-user"), null);
        assertNotNull(updated);

        graph.stream(null, updated).blockLast();

        // second 消费的是用户修改后的值，而非原始生成值
        assertEquals(1, executionCounts.getOrDefault(NODE_SECOND, 0));
        assertEquals("consumed:edited-by-user",
                graph.getState(config).state().value("secondOutput").orElse(null));
    }

    @Test
    @DisplayName("无中断点的图保持一次性跑完（回归：不影响现有 Skill）")
    void graphWithoutInterruptRunsToCompletion() throws Exception {
        KeyStrategyFactory factory = () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put("out", new ReplaceStrategy());
            return strategies;
        };
        StateGraph graph = new StateGraph(factory);
        graph.addNode("only", node_async(state -> Map.of("out", "done")));
        graph.addEdge(START, "only");
        graph.addEdge("only", END);

        // 与现有无确认节点的 Skill 一致：compile() 无参
        CompiledGraph compiled = graph.compile();
        var result = compiled.invoke(Map.of());

        assertTrue(result.isPresent());
        assertEquals("done", result.get().value("out").orElse(null));
    }
}
