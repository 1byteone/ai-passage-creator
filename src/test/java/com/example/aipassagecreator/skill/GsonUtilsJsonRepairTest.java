package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.utils.GsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GsonUtilsJsonRepairTest {

    @Test
    void tryFixJson_stripsCodeFence() {
        String input = "```json\n{\"a\": 1}\n```";
        assertEquals("{\"a\": 1}", GsonUtils.tryFixJson(input));
    }

    @Test
    void tryFixJson_trimsSurroundingText() {
        String input = "结果如下：{\"a\":1} 请查收";
        assertEquals("{\"a\":1}", GsonUtils.tryFixJson(input));
    }

    @Test
    void tryFixJson_fixesTruncatedBraces() {
        String input = "{\"a\":[1,2";
        String fixed = GsonUtils.tryFixJson(input);
        assertNotNull(GsonUtils.fromJsonSafe(fixed, Object.class));
    }

    @Test
    void tryFixJson_returnsEmptyObjectForBlank() {
        assertEquals("{}", GsonUtils.tryFixJson("   "));
    }
}
