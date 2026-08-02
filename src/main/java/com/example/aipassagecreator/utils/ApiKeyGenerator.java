package com.example.aipassagecreator.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * API Key 生成与哈希工具 — 明文 256-bit 随机，DB 只存 SHA-256
 */
public final class ApiKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "apc_";
    private static final int RANDOM_BYTES = 32;

    private ApiKeyGenerator() {
    }

    /** 生成 `apc_` + 43 位 Base64URL(无填充) 明文 token */
    public static String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex（64 位），用于落库比对 */
    public static String hash(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /** 脱敏前缀（前 10 位），用于列表展示 */
    public static String prefix(String apiKey) {
        return apiKey.substring(0, Math.min(apiKey.length(), 10));
    }
}
