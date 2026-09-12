package com.example.aipassagecreator.dataviz;

import com.example.aipassagecreator.skill.PhaseDefinition;
import com.example.aipassagecreator.skill.PromptTemplateEngine;
import com.example.aipassagecreator.skill.SkillController;
import com.example.aipassagecreator.skill.SkillDefinition;
import com.example.aipassagecreator.skill.SkillRegistry;
import com.example.aipassagecreator.skill.VariableDef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * data-visualization-report Skill 的 YAML 注册契约 + 对外可达性。
 * <p>只校验定义与提示词，不触发真实模型调用。
 */
@SpringBootTest
class DataVizSkillRegistrationTest {

    private static final String SKILL = "data-visualization-report";
    private static final String PHASE1_PROMPT = "skills/data-visualization-report/prompts/phase1_profile.md";
    private static final String PHASE2_PROMPT = "skills/data-visualization-report/prompts/phase2_charts.md";

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private PromptTemplateEngine promptTemplateEngine;

    @Autowired
    private SkillController skillController;

    private SkillDefinition definition() {
        return skillRegistry.getSkill(SKILL);
    }

    @Test
    void registration_hasTwoPhasesInOrder() {
        SkillDefinition def = definition();
        assertEquals(SKILL, def.getName());
        assertEquals(List.of("profile_dataset", "recommend_charts"),
                def.getPhases().stream().map(PhaseDefinition::getName).toList());
        assertEquals(List.of("datasetProfile", "chartSpecs"),
                def.getPhases().stream().map(PhaseDefinition::getOutputKey).toList());
    }

    @Test
    void registration_hasConfirmationOnlyInSecondPhase() {
        List<PhaseDefinition> phases = definition().getPhases();
        assertFalse(phases.get(0).isRequireConfirmation());
        PhaseDefinition second = phases.get(1);
        assertTrue(second.isRequireConfirmation());
        assertEquals("json", second.getOutputParser());
        assertEquals("chartSpecs", second.getOutputKey());
    }

    @Test
    void registration_phase2ConsumesProfileOutputByRef() {
        List<PhaseDefinition.VariableRef> refs = definition().getPhases().get(1).getVariables();
        // datasetProfile 必须引用 phase1 的 phase name，否则 AI 拿不到数据画像
        String profileRef = refs.stream()
                .filter(v -> "datasetProfile".equals(v.getName()))
                .findFirst().orElseThrow().getRef();
        assertEquals("profile_dataset", profileRef);
        assertTrue(refs.stream().anyMatch(v -> "style".equals(v.getName())));
    }

    @Test
    void registration_rawDataIsRequiredTextareaWithLimit() {
        VariableDef rawData = definition().getVariables().get("rawData");
        assertNotNull(rawData);
        assertTrue(rawData.isRequired());
        assertEquals("textarea", rawData.getUiType());
        assertEquals(200000, rawData.getMaxLength());
    }

    @Test
    void registration_dataFormatDefaultsToCsv() {
        VariableDef dataFormat = definition().getVariables().get("dataFormat");
        assertNotNull(dataFormat);
        assertEquals("csv", dataFormat.getDefaultValue());
    }

    @Test
    void registration_styleIsWhitelistedAndDefaultsToGlance() {
        VariableDef style = definition().getVariables().get("style");
        assertNotNull(style);
        assertEquals("glance", style.getDefaultValue());
        assertEquals(List.of("mono", "glance", "editorial"),
                style.getOptions().stream().map(o -> o.getValue().toString()).toList());
    }

    @Test
    void registration_goalIsOptional() {
        assertFalse(definition().getVariables().get("goal").isRequired());
    }

    @Test
    void promptTemplatesExistAndRenderDeclaredVariables() {
        // PromptTemplateEngine 使用单花括号占位符 {var}；文件缺失会抛 IllegalArgumentException
        String phase1 = promptTemplateEngine.render(PHASE1_PROMPT,
                Map.of("rawData", "月份,销量\n1月,120", "dataFormat", "csv", "goal", "看趋势"));
        assertTrue(phase1.contains("1月,120"));
        assertFalse(phase1.contains("{dataFormat}"), "占位符应已替换");
        assertTrue(phase1.contains("candidateInsights"));

        String phase2 = promptTemplateEngine.render(PHASE2_PROMPT,
                Map.of("datasetProfile", "{\"summary\":\"销量\"}", "goal", "看趋势", "style", "glance"));
        assertTrue(phase2.contains("销量"));
        assertFalse(phase2.contains("{style}"), "占位符应已替换");
        assertTrue(phase2.contains("chartSpecs") || phase2.contains("charts"));
    }

    @Test
    void publicSkill_isExposedInList() {
        // 白名单漏登记会让整个 Skill 不可达，定义再正确也是死代码
        List<Map<String, Object>> listed = skillController.listSkills().getData();
        assertTrue(listed.stream().anyMatch(s -> SKILL.equals(s.get("name"))),
                "skill 应在 /skill/list 中可见");
    }

    @Test
    void publicSkill_definitionIsReachable() {
        // getPublicSkill 对未登记 skill 抛 NOT_FOUND，此处断言不抛
        SkillDefinition viaController = skillController.getDefinition(SKILL).getData();
        assertEquals(SKILL, viaController.getName());
        assertEquals(2, viaController.getPhases().size());
    }
}
