package com.example.aipassagecreator.methodology;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MethodologyRegistryCardStyleTest {

    @Autowired
    private MethodologyRegistry registry;

    @Test
    void default_hasCardStyle() {
        MethodologyDefinition def = registry.get("default");
        assertNotNull(def.getPlatform());
        assertEquals("warm", def.getPlatform().getCardStyle());
    }

    @Test
    void xiaohongshu_cardStyleInherited() {
        MethodologyDefinition def = registry.get("xiaohongshu");
        assertEquals("warm", def.getPlatform().getCardStyle());
        assertEquals("小红书用户", def.getPlatform().getAudience());
    }
}
