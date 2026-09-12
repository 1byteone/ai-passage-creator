package com.example.aipassagecreator.dataviz;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * dataviz 产物文件访问：标识白名单 + 文件名越界拦截。
 * <p>
 * 产物由 {@link DataVizPostProcessor} 异步落盘到 {@code {user.dir}/data/dataviz/{executionId}/}，
 * 游标可能尚未产出文件，故 {@link #read} 以 null 表达「未就绪」而非抛异常。
 */
@Component
public class DataVizStorageService {

    /** Skill 执行 ID 一律是 {@code UUID.randomUUID()} 的字符串形式 */
    private static final Pattern EXEC_ID = Pattern.compile("[0-9a-fA-F-]{36}");

    public Path artifactDir(String executionId) {
        if (executionId == null || !EXEC_ID.matcher(executionId).matches()) {
            throw new IllegalArgumentException("非法的报告标识");
        }
        return Path.of(System.getProperty("user.dir"), "data", "dataviz", executionId);
    }

    public boolean exists(Path dir, String fileName) {
        return Files.isRegularFile(resolve(dir, fileName));
    }

    /**
     * @return 文件字节；不存在或读取失败返回 null（调用方按未就绪处理）
     */
    public byte[] read(Path dir, String fileName) {
        // resolve 的非法文件名是不可恢复的调用方错误，须在 try 外抛，否则会被下面的兜底吞成 null
        Path file = resolve(dir, fileName);
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return Files.readAllBytes(file);
        } catch (Exception e) {
            return null;
        }
    }

    private Path resolve(Path dir, String fileName) {
        // 文件名由本类调用方给定，此处仍拦截分隔符/上跳：artifactDir 只保证了目录本身在 data 下
        if (fileName == null || fileName.contains("/") || fileName.contains("\\")
                || fileName.contains("..")) {
            throw new IllegalArgumentException("非法的产物文件名");
        }
        return dir.resolve(fileName);
    }
}
