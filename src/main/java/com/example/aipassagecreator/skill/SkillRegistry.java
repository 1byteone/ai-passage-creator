package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Skill 注册中心
 * 扫描 classpath:skills/{skillName}/skill.yaml 自动注册并构建 StateGraph
 */
@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, SkillDefinition> skillMap = new LinkedHashMap<>();
    private final Map<String, CompiledGraph> graphCache = new ConcurrentHashMap<>();
    private final ResourceLoader resourceLoader;
    private final PromptTemplateEngine templateEngine;
    private final ModelRouter modelRouter;
    private final OutputParserRegistry parserRegistry;
    private final SkillExecutionMapper skillExecutionMapper;

    public SkillRegistry(ResourceLoader resourceLoader,
                         PromptTemplateEngine templateEngine,
                         ModelRouter modelRouter,
                         OutputParserRegistry parserRegistry,
                         SkillExecutionMapper skillExecutionMapper) {
        this.resourceLoader = resourceLoader;
        this.templateEngine = templateEngine;
        this.modelRouter = modelRouter;
        this.parserRegistry = parserRegistry;
        this.skillExecutionMapper = skillExecutionMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Resource[] resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
                    .getResources("classpath*:skills/*/skill.yaml");
            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                try {
                    SkillDefinition def = yaml.loadAs(resource.getInputStream(), SkillDefinition.class);
                    validateSkillDefinition(def);
                    skillMap.put(def.getName(), def);
                    CompiledGraph graph = buildGraph(def);
                    graphCache.put(def.getName(), graph);
                    log.info("Skill 已注册: {} ({} phases)", def.getName(), def.getPhases().size());
                } catch (Exception e) {
                    log.error("Skill 注册失败: {}", resource.getFilename(), e);
                }
            }
            log.info("SkillRegistry 初始化完成，共注册 {} 个 Skill", skillMap.size());
        } catch (Exception e) {
            log.error("SkillRegistry 扫描失败", e);
        }
    }

    public SkillDefinition getSkill(String name) {
        SkillDefinition def = skillMap.get(name);
        if (def == null) {
            throw new IllegalArgumentException("Skill 不存在: " + name + "，可用: " + skillMap.keySet());
        }
        return def;
    }

    public List<SkillDefinition> getAllSkills() {
        return List.copyOf(skillMap.values());
    }

    public CompiledGraph getGraph(String name) {
        CompiledGraph graph = graphCache.get(name);
        if (graph == null) {
            throw new IllegalArgumentException("Skill Graph 不存在: " + name);
        }
        return graph;
    }

    public SkillExecution createExecution(String skillName, Map<String, Object> inputs) {
        SkillDefinition def = getSkill(skillName);
        String executionId = UUID.randomUUID().toString();
        return new SkillExecution(executionId, def, inputs, graphCache.get(skillName), modelRouter, skillExecutionMapper);
    }

    public SkillExecutionChain createChain(String... skillNames) {
        return new SkillExecutionChain(skillNames, this);
    }

    private CompiledGraph buildGraph(SkillDefinition def) {
        StateGraph graph = new StateGraph(createKeyStrategy(def));
        try {
            // 添加节点
            String previousNode = START;
            for (int i = 0; i < def.getPhases().size(); i++) {
                PhaseDefinition phase = def.getPhases().get(i);
                String nodeName = phase.getName();
                SkillNodeAction action = new SkillNodeAction(phase, templateEngine, modelRouter, parserRegistry);
                graph.addNode(nodeName, node_async(action));
                graph.addEdge(previousNode, nodeName);
                previousNode = nodeName;
            }
            graph.addEdge(previousNode, END);

            return graph.compile();
        } catch (GraphStateException e) {
            throw new RuntimeException("StateGraph 编译失败: " + def.getName(), e);
        }
    }

    private KeyStrategyFactory createKeyStrategy(SkillDefinition def) {
        return () -> {
            Map<String, KeyStrategy> strategy = new HashMap<>();
            strategy.put("skillExecutionId", new ReplaceStrategy());
            strategy.put("skillName", new ReplaceStrategy());
            strategy.put("skillDefaultModel", new ReplaceStrategy());
            for (PhaseDefinition phase : def.getPhases()) {
                strategy.put(phase.getOutputKey(), new ReplaceStrategy());
                strategy.put(phase.getOutputKey() + "_raw", new ReplaceStrategy());
            }
            return strategy;
        };
    }

    private void validateSkillDefinition(SkillDefinition def) {
        Objects.requireNonNull(def.getName(), "Skill name 不能为空");
        Objects.requireNonNull(def.getPhases(), "Skill phases 不能为空");
        if (def.getPhases().isEmpty()) {
            throw new IllegalArgumentException("Skill " + def.getName() + " 至少需要一个 phase");
        }
    }
}