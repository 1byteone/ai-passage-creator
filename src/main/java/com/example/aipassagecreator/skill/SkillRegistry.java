package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.skill.tool.WebSearchTool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
    private final WebSearchTool webSearchTool;
    private SkillExecutionService skillExecutionService;

    public SkillRegistry(ResourceLoader resourceLoader,
                         PromptTemplateEngine templateEngine,
                         ModelRouter modelRouter,
                         OutputParserRegistry parserRegistry,
                         SkillExecutionMapper skillExecutionMapper,
                         WebSearchTool webSearchTool) {
        this.resourceLoader = resourceLoader;
        this.templateEngine = templateEngine;
        this.modelRouter = modelRouter;
        this.parserRegistry = parserRegistry;
        this.skillExecutionMapper = skillExecutionMapper;
        this.webSearchTool = webSearchTool;
    }

    @Autowired
    public void setSkillExecutionService(@Lazy SkillExecutionService skillExecutionService) {
        this.skillExecutionService = skillExecutionService;
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

    public SkillExecutionChain createChain(String chainExecutionId, String... skillNames) {
        return new SkillExecutionChain(skillNames, this, skillExecutionService, chainExecutionId);
    }

    public SkillExecutionChain createChain(String... skillNames) {
        return createChain(UUID.randomUUID().toString(), skillNames);
    }

    private CompiledGraph buildGraph(SkillDefinition def) {
        StateGraph graph = new StateGraph(createKeyStrategy(def));

        // 构建 phase name → outputKey 映射表
        Map<String, String> phaseOutputKeyMap = new HashMap<>();
        for (PhaseDefinition phase : def.getPhases()) {
            phaseOutputKeyMap.put(phase.getName(), phase.getOutputKey());
        }

        // 收集需要用户确认的节点：在这些节点执行「之前」中断
        List<String> confirmationNodes = new ArrayList<>();

        try {
            // 添加节点
            String previousNode = START;
            for (int i = 0; i < def.getPhases().size(); i++) {
                PhaseDefinition phase = def.getPhases().get(i);
                String nodeName = phase.getName();
                // 回填阶段序号，供前端定位进度
                phase.setPhaseIndex(i + 1);
                if (phase.isRequireConfirmation()) {
                    confirmationNodes.add(nodeName);
                }
                // 解析该阶段需要的工具
                List<ToolCallback> phaseTools = resolveTools(phase.getTools());
                SkillNodeAction action = new SkillNodeAction(
                        phase,
                        i + 1,
                        def.getPhases().size(),
                        templateEngine,
                        modelRouter,
                        parserRegistry,
                        phaseOutputKeyMap,
                        phaseTools
                );
                graph.addNode(nodeName, node_async(action));
                graph.addEdge(previousNode, nodeName);
                previousNode = nodeName;
            }
            graph.addEdge(previousNode, END);

            // 无确认节点的 Skill 保持原有编译路径，不引入检查点开销
            if (confirmationNodes.isEmpty()) {
                return graph.compile();
            }

            // 含确认节点：启用中断 + 检查点，使执行可在中断处暂停并稍后续跑
            log.info("Skill {} 启用多轮确认, 中断节点: {}", def.getName(), confirmationNodes);
            CompileConfig compileConfig = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder().register(new MemorySaver()).build())
                    .interruptsBefore(confirmationNodes)
                    .build();
            return graph.compile(compileConfig);
        } catch (GraphStateException e) {
            throw new RuntimeException("StateGraph 编译失败: " + def.getName(), e);
        }
    }

    /**
     * Skill 是否声明了需要用户确认的阶段
     */
    public boolean hasConfirmationPhase(String skillName) {
        SkillDefinition def = getSkill(skillName);
        return def.getPhases().stream().anyMatch(PhaseDefinition::isRequireConfirmation);
    }

    /**
     * 解析阶段声明中需要的工具列表
     * <p>
     * 当前支持：webSearch → WebSearchTool
     */
    private List<ToolCallback> resolveTools(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        for (String toolName : toolNames) {
            switch (toolName) {
                case "webSearch" -> callbacks.add(
                        FunctionToolCallback.builder("webSearch", webSearchTool::webSearch)
                                .description("执行网络搜索，返回结构化搜索结果（标题、链接、摘要）")
                                .inputType(WebSearchTool.WebSearchRequest.class)
                                .build()
                );
                default -> log.warn("未知工具: {}", toolName);
            }
        }
        return callbacks;
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
