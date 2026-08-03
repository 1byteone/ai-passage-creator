package com.example.aipassagecreator.handwriting.tools;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 手写字体批量上传工具（独立 main 类，不依赖 Spring context）。
 *
 * <p>用法：
 * <pre>
 *   TENCENT_COS_SECRET_ID=xxx TENCENT_COS_SECRET_KEY=xxx \
 *   TENCENT_COS_REGION=ap-guangzhou TENCENT_COS_BUCKET=xxx \
 *   mvn compile exec:java -Dexec.mainClass=com.example.aipassagecreator.handwriting.tools.FontUploader
 * </pre>
 * 或直接： {@code bash scripts/upload-handwriting-fonts.sh}
 */
@Slf4j
public class FontUploader {

    /** COS 上字体文件的存储路径前缀 */
    private static final String COS_PREFIX = "handwriting/fonts";

    /** 待上传字体目录（项目 resources） */
    private static final String FONTS_DIR =
            "src/main/resources/handwriting/fonts";

    public static void main(String[] args) {
        String secretId = System.getenv("TENCENT_COS_SECRET_ID");
        String secretKey = System.getenv("TENCENT_COS_SECRET_KEY");
        String region = System.getenv().getOrDefault("TENCENT_COS_REGION", "ap-guangzhou");
        String bucket = System.getenv("TENCENT_COS_BUCKET");

        if (secretId == null || secretId.isBlank()
                || secretKey == null || secretKey.isBlank()
                || bucket == null || bucket.isBlank()) {
            log.error("COS 环境变量缺失。需要设置: TENCENT_COS_SECRET_ID, "
                    + "TENCENT_COS_SECRET_KEY, TENCENT_COS_BUCKET (可选 TENCENT_COS_REGION)");
            System.exit(1);
        }

        File dir = Paths.get(FONTS_DIR).toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            log.error("字体目录不存在: {}", dir.getAbsolutePath());
            System.exit(1);
        }

        File[] fonts = dir.listFiles((d, name) ->
                name.endsWith(".ttf") || name.endsWith(".otf"));
        if (fonts == null || fonts.length == 0) {
            log.warn("字体目录下未找到 TTF/OTF 文件: {}", dir.getAbsolutePath());
            System.exit(0);
        }

        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        COSClient cosClient = new COSClient(cred, clientConfig);

        int success = 0;
        try {
            for (File font : fonts) {
                String cosKey = COS_PREFIX + "/" + font.getName();
                try {
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentLength(font.length());
                    String contentType = font.getName().endsWith(".ttf")
                            ? "font/ttf" : "font/otf";
                    metadata.setContentType(contentType);

                    PutObjectRequest putRequest =
                            new PutObjectRequest(bucket, cosKey, font)
                                    .withMetadata(metadata);
                    cosClient.putObject(putRequest);
                    log.info("✅ 上传成功: {} ({} MB) → cos://{}/{}",
                            font.getName(), font.length() / 1_048_576L, bucket, cosKey);
                    success++;
                } catch (Exception e) {
                    log.error("❌ 上传失败: {}", font.getName(), e);
                }
            }
            log.info("完成: {}/{} 个字体已上传到 cos://{}/{}",
                    success, fonts.length, bucket, COS_PREFIX);
        } finally {
            cosClient.shutdown();
        }

        if (success < fonts.length) {
            System.exit(1);
        }
    }
}
