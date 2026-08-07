package com.example.aipassagecreator.infra;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 迁移集兼容性验证 — 对独立 H2 内存库（MODE=MySQL）跑完整迁移集
 *
 * <p>应用测试（profile=test）用 {@code spring.sql.init} + h2-schema.sql，不走 Flyway；
 * 此处用 Flyway 官方 API 对<b>独立</b> H2 库执行 {@code classpath:db/migration}，
 * 证明迁移集是可移植 DDL（V1 在 H2 与 MySQL 8 均能建全量 schema），迁移集有 CI 覆盖。
 * 迁移文件与 h2-schema.sql 需同步维护：新增表 → V{n}__desc.sql + h2-schema.sql。</p>
 */
class FlywayMigrationCompatibilityTest {

    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flyway_compat;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
    }

    @Test
    @DisplayName("迁移集可在 H2 MODE=MySQL 全量执行并落 schema history")
    void migrate_allVersions_buildsSchemaHistoryAndTables() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        MigrateResult result = flyway.migrate();

        assertTrue(result.success, "Flyway migrate 应成功");
        assertTrue(result.migrationsExecuted >= 1, "应至少执行 1 个迁移");

        // 已应用版本（Flyway API 读取，规避 H2 MySQL 模式标识符大小写）
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(v -> v == null ? "" : v.getVersion())
                .toList();
        assertTrue(appliedVersions.contains("1"), "V1 基线应成功应用: " + appliedVersions);
        assertTrue(appliedVersions.contains("2"), "V2 检查点表应成功应用: " + appliedVersions);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 关键表已创建
            List<String> tables = listTables(stmt);
            for (String table : List.of("user", "article", "skill_execution", "article_quality",
                    "workspace", "approval_record", "publish_schedule", "article_card", "api_key",
                    "skill_checkpoint")) {
                assertTrue(tables.contains(table), "迁移后应存在表 " + table);
            }

            // 种子数据已写入
            try (ResultSet rs = stmt.executeQuery("select count(*) from `user`")) {
                rs.next();
                assertTrue(rs.getInt(1) >= 3, "user 表应含种子数据");
            }
        }
    }

    @Test
    @DisplayName("迁移版本号在 db/migration + db/vendor/mysql 合并后唯一（防同版本冲突启动失败）")
    void migrationVersions_uniqueAcrossVendorLocations() {
        // 生产 MySQL 的 flyway.locations 同时扫 db/migration 与 db/vendor/mysql。
        // 若两个文件声明同版本（如历史曾出现的两个 V3），Flyway 启动即抛
        // "Found more than one migration with version N"。此处不执行 SQL（vendor 的
        // 存储过程仅 MySQL 可跑），仅用 info() 解析合并后的版本集并断言唯一。
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/vendor/mysql")
                .load();

        List<String> versions = Arrays.stream(flyway.info().all())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .map(v -> v.getVersion())
                .toList();

        assertEquals(versions.size(), versions.stream().distinct().count(),
                "db/migration 与 db/vendor/mysql 合并后的版本号必须唯一: " + versions);
        assertTrue(versions.contains("4"), "character_style 迁移应已改名到 V4: " + versions);
    }

    private List<String> listTables(Statement stmt) throws Exception {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(
                "select table_name from information_schema.tables where table_schema = current_schema()")) {
            while (rs.next()) {
                tables.add(rs.getString("table_name").toLowerCase());
            }
        }
        return tables;
    }
}
