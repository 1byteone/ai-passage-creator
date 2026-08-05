package com.example.aipassagecreator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * RAG 向量存储配置 — Supabase + pgvector 主 / 内存 SimpleVectorStore 降级
 *
 * <p>选择逻辑（{@code rag.vectorstore.active}）：</p>
 * <ul>
 *   <li>{@code auto}（默认）：有 SUPABASE_DB_URL 凭据 → PgVectorStore；否则 → 内存 SimpleVectorStore（开发可跑，不阻塞）</li>
 *   <li>{@code pgvector}：强制 PgVectorStore（须配置 Supabase 凭据）</li>
 *   <li>{@code memory}：强制内存 SimpleVectorStore</li>
 * </ul>
 *
 * <p>注意：主 DataSource 是 MySQL，PgVectorStore 需要独立的 Postgres 数据源指向 Supabase，
 * 因此这里用 {@link DriverManagerDataSource} 单独建 Postgres DataSource，不触碰主数据源。</p>
 */
@Slf4j
@Configuration
public class RagVectorStoreConfig {

    private static final String PG_DRIVER = "org.postgresql.Driver";

    @Bean
    @Primary
    public VectorStore vectorStore(
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            @Value("${rag.vectorstore.active:auto}") String active,
            @Value("${SUPABASE_DB_URL:}") String supabaseUrl,
            @Value("${SUPABASE_DB_USER:postgres}") String supabaseUser,
            @Value("${SUPABASE_DB_PASSWORD:}") String supabasePassword) {

        boolean usePgVector = switch (active) {
            case "pgvector" -> true;
            case "memory" -> false;
            default -> !supabaseUrl.isBlank() && !supabasePassword.isBlank();
        };

        if (usePgVector) {
            log.info("RAG 向量存储启用 PgVectorStore（Supabase + pgvector）: url={}", maskUrl(supabaseUrl));
            try {
                return buildPgVectorStore(embeddingModel, supabaseUrl, supabaseUser, supabasePassword);
            } catch (Exception e) {
                log.warn("PgVectorStore 连接失败，降级到内存 SimpleVectorStore: {}", e.getMessage());
            }
        }
        log.warn("RAG 向量存储启用内存 SimpleVectorStore（未配置 Supabase 凭据/连接失败/主动降级，重启后数据丢失）");
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /** 构建指向 Supabase Postgres 的 PgVectorStore（HNSW + 余弦距离，自动建表） */
    private PgVectorStore buildPgVectorStore(EmbeddingModel embeddingModel,
                                             String url, String user, String password) {
        DataSource ds = buildPostgresDataSource(url, user, password);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        // 显式获取 embedding 维度，避免 pgvector 建表时 create vector(0) 失败
        int dims = embeddingModel.dimensions();
        if (dims <= 0) {
            log.warn("EmbeddingModel 返回维度 {}，使用默认 1536", dims);
            dims = 1536;
        }
        log.info("PgVectorStore 初始化: dimensions={}, index=HNSW, distance=COSINE", dims);
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dims)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .initializeSchema(true)
                .build();
    }

    /** 独立的 Postgres DataSource，不碰主 MySQL 数据源 */
    private DataSource buildPostgresDataSource(String url, String user, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(PG_DRIVER);
        // prepareThreshold=0: 禁用服务端 prepared statement。
        // Supabase Supavisor(pooler) transaction mode 不支持服务端 prepared statement 缓存，
        // 默认阈值 5 会在长连接复用时报 "prepared statement S_1 already exists"，导致批量插入失败。
        ds.setUrl(appendUrlParam(url, "prepareThreshold", "0"));
        ds.setUsername(user);
        ds.setPassword(password);
        return ds;
    }

    /** 给 JDBC URL 追加参数（已有 query 用 & 分隔，否则用 ?） */
    private String appendUrlParam(String url, String key, String value) {
        if (url == null || url.isBlank()) return url;
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + key + "=" + value;
    }

    /** 脱敏 URL（隐藏密码/密钥段）便于日志 */
    private String maskUrl(String url) {
        if (url == null || url.isBlank()) return "(空)";
        return url.replaceAll("(?<=://)[^:@/]+:[^@/]+@", "***:***@");
    }
}
