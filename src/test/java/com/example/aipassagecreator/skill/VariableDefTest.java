package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.utils.GsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VariableDef 序列化/反序列化测试
 * <p>
 * 确保 fieldType、options 等前端表单渲染元数据在 JSON 往返后保持一致。
 */
class VariableDefTest {

    @Test
    void shouldSerializeUiType() {
        VariableDef def = new VariableDef();
        def.setName("topic");
        def.setUiType("textarea");
        def.setRequired(true);
        def.setPlaceholder("描述你的选题方向");
        def.setMaxLength(1000);

        String json = GsonUtils.toJson(def);
        assertTrue(json.contains("\"uiType\":\"textarea\""));
        assertTrue(json.contains("\"maxLength\":1000"));
        assertTrue(json.contains("\"placeholder\":\"描述你的选题方向\""));
        assertTrue(json.contains("\"required\":true"));
    }

    @Test
    void shouldRoundTripOptions() {
        VariableDef def = new VariableDef();
        def.setName("platform");
        def.setUiType("select");
        def.setDefaultValue("weibo");

        VariableDef.OptionDef opt1 = new VariableDef.OptionDef();
        opt1.setLabel("微博");
        opt1.setValue("weibo");
        VariableDef.OptionDef opt2 = new VariableDef.OptionDef();
        opt2.setLabel("小红书");
        opt2.setValue("xiaohongshu");
        def.setOptions(List.of(opt1, opt2));

        String json = GsonUtils.toJson(def);
        VariableDef restored = GsonUtils.fromJson(json, VariableDef.class);

        assertEquals("platform", restored.getName());
        assertEquals("select", restored.getUiType());
        assertEquals("weibo", restored.getDefaultValue());
        assertEquals(2, restored.getOptions().size());
        assertEquals("微博", restored.getOptions().get(0).getLabel());
        assertEquals("weibo", restored.getOptions().get(0).getValue());
    }

    @Test
    void shouldDefaultUiTypeToInput() {
        VariableDef def = new VariableDef();
        def.setName("custom");

        String json = GsonUtils.toJson(def);
        VariableDef restored = GsonUtils.fromJson(json, VariableDef.class);

        assertEquals("input", restored.getUiType());
        assertEquals("INPUT", restored.getSource());
        assertFalse(restored.isRequired());
    }

    @Test
    void shouldHandlePhaseRefForIntermediateVariables() {
        VariableDef def = new VariableDef();
        def.setName("searchResults");
        def.setSource("PHASE_OUTPUT");
        def.setPhaseRef("search");

        String json = GsonUtils.toJson(def);
        VariableDef restored = GsonUtils.fromJson(json, VariableDef.class);

        assertEquals("PHASE_OUTPUT", restored.getSource());
        assertEquals("search", restored.getPhaseRef());
    }

    @Test
    void shouldSerializeNullOptionsAsAbsent() {
        VariableDef def = new VariableDef();
        def.setName("topic");
        def.setUiType("input");

        String json = GsonUtils.toJson(def);
        VariableDef restored = GsonUtils.fromJson(json, VariableDef.class);

        assertEquals("input", restored.getUiType());
        assertNull(restored.getOptions());
        assertNull(restored.getDefaultValue());
    }
}
