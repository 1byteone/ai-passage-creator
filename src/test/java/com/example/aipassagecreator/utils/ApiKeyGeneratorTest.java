package com.example.aipassagecreator.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyGeneratorTest {

    @Test
    void generate_hasPrefixAndExpectedLength() {
        String token = ApiKeyGenerator.generate();

        assertTrue(token.startsWith("apc_"));
        // apc_ + 43 位 Base64URL（32 字节 → 43 字符）
        assertEquals(47, token.length());
    }

    @Test
    void generate_distinctTokens() {
        assertNotEquals(ApiKeyGenerator.generate(), ApiKeyGenerator.generate());
    }

    @Test
    void hash_isDeterministic() {
        String token = "apc_abc123";

        assertEquals(ApiKeyGenerator.hash(token), ApiKeyGenerator.hash(token));
        assertNotEquals(ApiKeyGenerator.hash(token), ApiKeyGenerator.hash("apc_abc124"));
    }

    @Test
    void hash_returns64HexChars() {
        assertEquals(64, ApiKeyGenerator.hash("apc_x").length());
    }

    @Test
    void prefix_masksToken() {
        String token = ApiKeyGenerator.generate();
        String prefix = ApiKeyGenerator.prefix(token);

        // 前 10 位，不泄露完整明文
        assertEquals(10, prefix.length());
        assertTrue(token.startsWith(prefix));
        assertFalse(prefix.equals(token));
    }
}
