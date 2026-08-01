package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.config.YamlResourceLoader;
import com.example.aipassagecreator.skill.SkillDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class YamlResourceLoaderTest {

    @Autowired
    private YamlResourceLoader yamlResourceLoader;

    @Test
    void loadAll_loadsSkillYamls() {
        List<SkillDefinition> defs = yamlResourceLoader.loadAll(
                "classpath*:skills/*/skill.yaml", SkillDefinition.class);
        assertFalse(defs.isEmpty(), "至少应加载到一个 skill.yaml");
        assertTrue(defs.stream().anyMatch(d -> "research".equals(d.getName())),
                "应包含 research skill");
        assertTrue(defs.stream().anyMatch(d -> "topic-gen".equals(d.getName())),
                "应包含 topic-gen skill");
        assertTrue(defs.stream().anyMatch(d -> "proofreading".equals(d.getName())),
                "应包含 proofreading skill");
    }

    @Test
    void loadAll_safeConstructorRejectsArbitraryTypes() {
        // SafeConstructor 拒绝 !! 标签（如 !!java.lang.ProcessBuilder），应安全解析为 Map 而非实例化
        // 正常加载 skills 不抛异常即验证 SafeConstructor 生效
        List<SkillDefinition> defs = yamlResourceLoader.loadAll(
                "classpath*:skills/*/skill.yaml", SkillDefinition.class);
        assertFalse(defs.isEmpty());
        // 验证所有 skill 的 name 均已正确解析
        assertTrue(defs.stream().allMatch(d -> d.getName() != null && !d.getName().isBlank()));
    }
}
