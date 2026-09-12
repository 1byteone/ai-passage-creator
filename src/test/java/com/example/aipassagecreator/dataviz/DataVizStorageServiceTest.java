package com.example.aipassagecreator.dataviz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * dataviz 产物文件访问：标识白名单 + 文件名越界拦截 + 缺失降级。
 * <p>
 * artifactDir 的根目录来自 user.dir，测试只断言路径形状而不落盘，避免污染工作区；
 * 读写行为用 @TempDir 覆盖。
 */
class DataVizStorageServiceTest {

    private static final String UUID_ID = "550e8400-e29b-41d4-a716-446655440000";

    private final DataVizStorageService storage = new DataVizStorageService();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("非 UUID 标识 → 拒绝（防目录穿越）")
    void artifactDir_rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> storage.artifactDir("../../etc"));
        assertThrows(IllegalArgumentException.class, () -> storage.artifactDir("exec/../../escape"));
        assertThrows(IllegalArgumentException.class, () -> storage.artifactDir(""));
        assertThrows(IllegalArgumentException.class, () -> storage.artifactDir(null));
        // 长度差一位也不算合法标识
        assertThrows(IllegalArgumentException.class,
                () -> storage.artifactDir("550e8400-e29b-41d4-a716-44665544000"));
    }

    @Test
    @DisplayName("UUID 标识 → 解析为 {user.dir}/data/dataviz/{id}")
    void artifactDir_acceptsUuid() {
        Path dir = storage.artifactDir(UUID_ID);

        assertTrue(dir.isAbsolute());
        assertEquals(Path.of(System.getProperty("user.dir"), "data", "dataviz", UUID_ID), dir);
    }

    @Test
    @DisplayName("产物缺失 → exists false / read null（未生成不是错误）")
    void missingArtifact_returnsFalseAndNull() {
        assertFalse(storage.exists(tempDir, "report.html"));
        assertNull(storage.read(tempDir, "report.png"));
    }

    @Test
    @DisplayName("产物存在 → exists true / read 返回原始字节")
    void presentArtifact_readsBytes() throws Exception {
        Files.writeString(tempDir.resolve("report.html"), "<html>报告</html>");

        assertTrue(storage.exists(tempDir, "report.html"));
        assertArrayEquals("<html>报告</html>".getBytes(StandardCharsets.UTF_8),
                storage.read(tempDir, "report.html"));
    }

    @Test
    @DisplayName("文件名向上越界 → 拒绝，不读产物目录外的文件")
    void fileNameEscapingDir_isRejected() throws Exception {
        Files.writeString(tempDir.resolve("secret.txt"), "secret");

        assertThrows(IllegalArgumentException.class, () -> storage.read(tempDir, "../secret.txt"));
        assertThrows(IllegalArgumentException.class, () -> storage.exists(tempDir, "../secret.txt"));
    }
}
