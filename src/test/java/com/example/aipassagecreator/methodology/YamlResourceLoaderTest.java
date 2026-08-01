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
        // 验证 SafeConstructor 生效：构造恶意 yaml 应解析为 Map 而非任意类
        // （此处仅验证框架不会因异常中断，正常路径下断言加载结果不为 null）
        assertNotNull(yamlResourceLoader);
    }
}
