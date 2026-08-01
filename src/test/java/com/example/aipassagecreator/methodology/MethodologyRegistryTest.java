package com.example.aipassagecreator.methodology;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MethodologyRegistryTest {

    @Autowired
    private MethodologyRegistry registry;

    @Test
    void default_existsWithDimensions() {
        MethodologyDefinition def = registry.get("default");
        assertNotNull(def);
        assertNotNull(def.getCreationDimensions());
        assertFalse(def.getCreationDimensions().isEmpty());
        assertNotNull(def.getTitleStrategies());
        assertEquals(5, def.getTitleStrategies().size(), "标题策略应为 5 种");
        assertNotNull(def.getEvaluationDimensions());
    }

    @Test
    void wechat_inheritsFromDefault() {
        MethodologyDefinition wechat = registry.get("wechat");
        assertNotNull(wechat);
        // wechat 未显式声明创建维度，应继承 default 的
        assertNotNull(wechat.getCreationDimensions());
        assertFalse(wechat.getCreationDimensions().isEmpty());
        // 平台规则已物化
        assertNotNull(wechat.getPlatform());
        assertEquals("wechat", wechat.getPlatform().getName());
    }

    @Test
    void unknownName_throws() {
        assertThrows(IllegalArgumentException.class, () -> registry.get("not-exist"));
    }

    @Test
    void weights_normalizable() {
        MethodologyDefinition def = registry.get("default");
        int sum = def.getEvaluationDimensions().stream()
                .mapToInt(d -> d.getWeight() == null ? 0 : d.getWeight())
                .sum();
        assertTrue(sum > 0, "权重之和必须大于 0");
    }

    @Test
    void dimensionKeys_crossReferencedConsistent() {
        // 评测维度中交叉引用的 key（如 titleStrategy）必须能被标题策略找到
        MethodologyDefinition def = registry.get("default");
        assertTrue(def.getTitleStrategies().stream()
                .anyMatch(s -> "curiosityGap".equals(s.getKey())));
    }
}
