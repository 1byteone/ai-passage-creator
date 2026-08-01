package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.config.YamlResourceLoader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方法论注册中心。
 * <p>启动期扫描 {@code classpath*:methodology/*.yaml}，物化 {@code parent} 继承、
 * 检测继承环、校验维度 key 一致性与权重合法，加载为不可变 Map。</p>
 */
@Slf4j
@Component
public class MethodologyRegistry {

    private final Map<String, MethodologyDefinition> methodologyMap = new ConcurrentHashMap<>();
    private final YamlResourceLoader yamlResourceLoader;

    public MethodologyRegistry(YamlResourceLoader yamlResourceLoader) {
        this.yamlResourceLoader = yamlResourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            List<MethodologyDefinition> rawDefs = yamlResourceLoader.loadAll(
                    "classpath*:methodology/*.yaml", MethodologyDefinition.class);

            Map<String, MethodologyDefinition> rawMap = new HashMap<>();
            for (MethodologyDefinition def : rawDefs) {
                if (def.getName() == null || def.getName().isBlank()) {
                    log.warn("方法论缺少 name, 跳过: {}", def);
                    continue;
                }
                rawMap.put(def.getName(), def);
            }

            Map<String, MethodologyDefinition> materialized = new HashMap<>();
            for (String name : rawMap.keySet()) {
                materialized.put(name, materialize(name, rawMap, new HashSet<>()));
            }

            if (!materialized.containsKey("default")) {
                materialized.put("default", builtinDefault());
            }

            for (MethodologyDefinition def : materialized.values()) {
                validate(def);
            }

            methodologyMap.putAll(materialized);
            log.info("MethodologyRegistry 初始化完成，共 {} 个方法论: {}", methodologyMap.size(), methodologyMap.keySet());
        } catch (Exception e) {
            log.error("MethodologyRegistry 初始化失败，使用内置兜底", e);
            methodologyMap.clear();
            methodologyMap.put("default", builtinDefault());
        }
    }

    public MethodologyDefinition get(String name) {
        MethodologyDefinition def = methodologyMap.get(name);
        if (def == null) {
            throw new IllegalArgumentException("方法论不存在: " + name + "，可用: " + methodologyMap.keySet());
        }
        return def;
    }

    public List<String> getNames() {
        return List.copyOf(methodologyMap.keySet());
    }

    public boolean exists(String name) {
        return name != null && methodologyMap.containsKey(name);
    }

    public MethodologyDefinition getDefault() {
        return methodologyMap.getOrDefault("default", builtinDefault());
    }

    /**
     * 物化继承：DFS 解析 parent 链，检测环，合并字段。
     * 合并规则：标量子覆盖父；集合（creationDimensions/titleStrategies/evaluationDimensions）整体替换；
     * platform.evaluationWeights 按 key 对父权重 patch。
     */
    private MethodologyDefinition materialize(String name, Map<String, MethodologyDefinition> rawMap,
                                              Set<String> visited) {
        if (visited.contains(name)) {
            throw new IllegalArgumentException("方法论继承环检测: " + visited + " -> " + name);
        }
        MethodologyDefinition raw = rawMap.get(name);
        if (raw == null) {
            throw new IllegalArgumentException("方法论 parent 不存在: " + name);
        }

        String parentName = raw.getParent();
        if (parentName == null || parentName.isBlank()) {
            return raw;
        }

        visited.add(name);
        MethodologyDefinition parent = materialize(parentName, rawMap, visited);
        visited.remove(name);

        MethodologyDefinition merged = new MethodologyDefinition();
        merged.setName(raw.getName());
        merged.setDescription(raw.getDescription() != null ? raw.getDescription() : parent.getDescription());
        merged.setVersion(raw.getVersion() != null ? raw.getVersion() : parent.getVersion());
        merged.setParent(null); // 物化后不再依赖继承链
        merged.setCreationDimensions(raw.getCreationDimensions() != null
                ? raw.getCreationDimensions() : parent.getCreationDimensions());
        merged.setTitleStrategies(raw.getTitleStrategies() != null
                ? raw.getTitleStrategies() : parent.getTitleStrategies());
        merged.setPlatform(mergePlatform(raw.getPlatform(), parent.getPlatform()));
        // 平台 evaluationWeights 物化到评测维度权重：key 命中则覆盖 dim.weight（先深拷贝，避免污染继承链上父方法的共享维度对象）
        merged.setEvaluationDimensions(applyPlatformWeights(
                raw.getEvaluationDimensions() != null
                        ? raw.getEvaluationDimensions() : parent.getEvaluationDimensions(),
                merged.getPlatform()));
        return merged;
    }

    /**
     * 将 {@code platform.evaluationWeights} 应用到评测维度列表。
     * <p>返回全新的列表与维度对象（深拷贝），仅对 key 命中 map 的维度覆盖 weight；
     * 未命中的维度保留继承权重。深拷贝防止就地修改污染父方法论的共享维度实例。</p>
     */
    private List<MethodologyDefinition.EvaluationDimension> applyPlatformWeights(
            List<MethodologyDefinition.EvaluationDimension> dims,
            MethodologyDefinition.PlatformConfig platform) {
        if (dims == null) {
            return null;
        }
        Map<String, Integer> weights = platform != null ? platform.getEvaluationWeights() : null;
        List<MethodologyDefinition.EvaluationDimension> result = new ArrayList<>(dims.size());
        for (MethodologyDefinition.EvaluationDimension d : dims) {
            MethodologyDefinition.EvaluationDimension copy = new MethodologyDefinition.EvaluationDimension();
            copy.setKey(d.getKey());
            copy.setName(d.getName());
            copy.setWeight(d.getWeight());
            copy.setRubric(d.getRubric());
            if (weights != null && weights.containsKey(copy.getKey()) && weights.get(copy.getKey()) != null) {
                copy.setWeight(weights.get(copy.getKey()));
            }
            result.add(copy);
        }
        return result;
    }

    private MethodologyDefinition.PlatformConfig mergePlatform(
            MethodologyDefinition.PlatformConfig child,
            MethodologyDefinition.PlatformConfig parent) {
        if (child == null) {
            return parent;
        }
        if (parent == null) {
            return child;
        }
        MethodologyDefinition.PlatformConfig merged = new MethodologyDefinition.PlatformConfig();
        merged.setName(child.getName() != null ? child.getName() : parent.getName());
        merged.setAudience(child.getAudience() != null ? child.getAudience() : parent.getAudience());
        merged.setMinChars(child.getMinChars() != null ? child.getMinChars() : parent.getMinChars());
        merged.setMaxChars(child.getMaxChars() != null ? child.getMaxChars() : parent.getMaxChars());
        merged.setStyle(child.getStyle() != null ? child.getStyle() : parent.getStyle());
        if (child.getEvaluationWeights() != null || parent.getEvaluationWeights() != null) {
            Map<String, Integer> weights = new HashMap<>();
            if (parent.getEvaluationWeights() != null) {
                weights.putAll(parent.getEvaluationWeights());
            }
            if (child.getEvaluationWeights() != null) {
                weights.putAll(child.getEvaluationWeights());
            }
            merged.setEvaluationWeights(weights);
        }
        return merged;
    }

    /**
     * 校验：维度 key 非空、权重合法、Σweight>0、交叉引用 key 一致。
     */
    private void validate(MethodologyDefinition def) {
        List<MethodologyDefinition.EvaluationDimension> evalDims = def.getEvaluationDimensions();
        if (evalDims != null) {
            int sum = 0;
            for (MethodologyDefinition.EvaluationDimension d : evalDims) {
                if (d.getKey() == null || d.getKey().isBlank()) {
                    throw new IllegalArgumentException("方法论 " + def.getName() + " 评测维度缺少 key");
                }
                int w = d.getWeight() == null ? 0 : d.getWeight();
                if (w <= 0 || w > 100) {
                    throw new IllegalArgumentException("方法论 " + def.getName() + " 维度 "
                            + d.getKey() + " 权重非法: " + w + "（需在 (0,100]）");
                }
                sum += w;
            }
            if (sum <= 0) {
                throw new IllegalArgumentException("方法论 " + def.getName() + " 评测维度权重之和必须大于 0");
            }
        }
    }

    /** 内置兜底 default：不依赖任何文件，保证 getDefault 永不返回 null */
    private MethodologyDefinition builtinDefault() {
        MethodologyDefinition def = new MethodologyDefinition();
        def.setName("default");
        def.setDescription("内置兜底爆款方法论");
        def.setVersion("1.0");
        def.setCreationDimensions(List.of(createDim("coreViewpoint", "核心观点", "一句话点明核心观点"),
                createDim("persuasion", "说服策略", "运用数据/故事/类比等说服手段"),
                createDim("emotionalTrigger", "情绪触发", "触及读者情绪，引起共鸣")));
        def.setTitleStrategies(List.of(newTitleStrategy("curiosityGap", "好奇心缺口"),
                newTitleStrategy("dataImpact", "数据冲击"),
                newTitleStrategy("painResonance", "痛点共鸣"),
                newTitleStrategy("counterIntuitive", "反常识"),
                newTitleStrategy("socialCurrency", "社交货币")));
        def.setEvaluationDimensions(List.of(
                newEvalDim("emotionalTrigger", "情感触发", 40),
                newEvalDim("goldenSentence", "金句", 30),
                newEvalDim("persuasion", "说服策略", 30)));
        return def;
    }

    private MethodologyDefinition.CreationDimension createDim(String key, String name, String guidance) {
        MethodologyDefinition.CreationDimension d = new MethodologyDefinition.CreationDimension();
        d.setKey(key);
        d.setName(name);
        d.setGuidance(guidance);
        return d;
    }

    private MethodologyDefinition.TitleStrategy newTitleStrategy(String key, String name) {
        MethodologyDefinition.TitleStrategy s = new MethodologyDefinition.TitleStrategy();
        s.setKey(key);
        s.setName(name);
        return s;
    }

    private MethodologyDefinition.EvaluationDimension newEvalDim(String key, String name, int weight) {
        MethodologyDefinition.EvaluationDimension d = new MethodologyDefinition.EvaluationDimension();
        d.setKey(key);
        d.setName(name);
        d.setWeight(weight);
        d.setRubric(name);
        return d;
    }
}
