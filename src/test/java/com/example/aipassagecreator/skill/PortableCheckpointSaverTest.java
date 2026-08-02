package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PortableCheckpointSaver 单元测试 — 用独立 H2 内存库（MODE=MySQL）验证
 * put/get/list/release 语义、跨 saver 实例读回（模拟重启）与不可序列化值归一化。
 */
class PortableCheckpointSaverTest {

    private JdbcDataSource dataSource;
    private PortableCheckpointSaver saver;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cp_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        try (Connection conn = dataSource.getConnection()) {
            // 复用 V2 迁移 DDL，保证单测与迁移集定义的表结构一致
            ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("db/migration/V2__add_skill_checkpoint.sql"));
        }
        saver = new PortableCheckpointSaver(new JdbcTemplate(dataSource));
    }

    private Checkpoint checkpoint(String id, String nodeId, String nextNodeId, Map<String, Object> state) {
        return Checkpoint.builder()
                .id(id)
                .nodeId(nodeId)
                .nextNodeId(nextNodeId)
                .state(state)
                .build();
    }

    @Test
    @DisplayName("put→get 往返：新检查点返回携带 id 的 config，可按 id 精确读取")
    void putGet_roundTrip() throws Exception {
        Checkpoint cp = checkpoint("cp-1", "search", "summary",
                Map.of("searchResults", Map.of("topic", "AI")));

        RunnableConfig config = RunnableConfig.builder().threadId("exec-1").build();
        RunnableConfig returned = saver.put(config, cp);

        assertTrue(returned.checkPointId().isPresent(), "put 应返回携带 checkPointId 的 config");
        assertEquals("cp-1", returned.checkPointId().get());

        Checkpoint loaded = saver.get(returned).orElseThrow();
        assertEquals("cp-1", loaded.getId());
        assertEquals("summary", loaded.getNextNodeId());
        assertEquals(Map.of("searchResults", Map.of("topic", "AI")), loaded.getState());
    }

    @Test
    @DisplayName("无 checkPointId 的 get 返回该线程最新检查点")
    void getWithoutId_returnsLatest() throws Exception {
        saver.put(RunnableConfig.builder().threadId("exec-l").build(),
                checkpoint("cp-a", "first", "second", Map.of("step", "a")));
        saver.put(RunnableConfig.builder().threadId("exec-l").build(),
                checkpoint("cp-b", "second", "END", Map.of("step", "b")));

        Checkpoint loaded = saver.get(RunnableConfig.builder().threadId("exec-l").build()).orElseThrow();
        assertEquals("cp-b", loaded.getId(), "应取到最新写入的检查点");
    }

    @Test
    @DisplayName("config 带 checkPointId 的 put 为原地更新，不产生重复行")
    void putWithId_updatesExistingRow() throws Exception {
        Checkpoint cp = checkpoint("cp-u", "first", "second", Map.of("step", "a"));
        RunnableConfig config = RunnableConfig.builder().threadId("exec-u").build();
        RunnableConfig returned = saver.put(config, cp);

        Checkpoint updated = checkpoint("cp-u", "first", "summary2", Map.of("step", "b"));
        saver.put(returned, updated);

        Checkpoint loaded = saver.get(returned).orElseThrow();
        assertEquals("summary2", loaded.getNextNodeId(), "应覆盖原行内容");
        int rows = new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from skill_checkpoint where thread_id = ?", Integer.class, "exec-u");
        assertEquals(1, rows, "原地更新不应产生重复行");
    }

    @Test
    @DisplayName("list 返回该线程全部检查点，按写入倒序")
    void list_returnsAllForThread() throws Exception {
        saver.put(RunnableConfig.builder().threadId("exec-ll").build(),
                checkpoint("cp-1", "first", "second", Map.of("step", 1)));
        saver.put(RunnableConfig.builder().threadId("exec-ll").build(),
                checkpoint("cp-2", "second", "END", Map.of("step", 2)));

        List<Checkpoint> all = List.copyOf(saver.list(RunnableConfig.builder().threadId("exec-ll").build()));
        assertEquals(2, all.size());
        assertEquals("cp-2", all.get(0).getId(), "list 应按 id 倒序返回");
        assertEquals("cp-1", all.get(1).getId());

        // 线程隔离：另一线程查不到
        assertTrue(saver.list(RunnableConfig.builder().threadId("other").build()).isEmpty());
    }

    @Test
    @DisplayName("release 删除该线程全部检查点并返回 Tag")
    void release_removesAndReturnsTag() throws Exception {
        saver.put(RunnableConfig.builder().threadId("exec-r").build(),
                checkpoint("cp-1", "first", "second", Map.of("step", 1)));

        BaseCheckpointSaver.Tag tag = saver.release(RunnableConfig.builder().threadId("exec-r").build());
        assertEquals("exec-r", tag.threadId());
        assertEquals(1, tag.checkpoints().size());

        assertTrue(saver.get(RunnableConfig.builder().threadId("exec-r").build()).isEmpty(),
                "release 后不应再能读取");
    }

    @Test
    @DisplayName("跨 saver 实例读回同一份检查点（模拟应用重启）")
    void crossInstance_readsSameData() throws Exception {
        saver.put(RunnableConfig.builder().threadId("exec-re").build(),
                checkpoint("cp-1", "first", "second", Map.of("step", "persisted")));

        PortableCheckpointSaver restarted = new PortableCheckpointSaver(new JdbcTemplate(dataSource));
        Checkpoint loaded = restarted.get(RunnableConfig.builder().threadId("exec-re").build()).orElseThrow();
        assertEquals("cp-1", loaded.getId());
        assertEquals(Map.of("step", "persisted"), loaded.getState());
    }

    @Test
    @DisplayName("Gson json 产物（嵌套 Map/List）经检查点存取后结构完整保留")
    void gsonJsonOutput_roundTripsThroughCheckpoint() throws Exception {
        Gson gson = new Gson();
        Map<String, Object> gsonMap = gson.fromJson(
                "{\"results\":[{\"title\":\"a\"},{\"title\":\"b\"}]}",
                new TypeToken<Map<String, Object>>() {
                }.getType());

        saver.put(RunnableConfig.builder().threadId("exec-g").build(),
                checkpoint("cp-g", "search", "summary", Map.of("searchResults", gsonMap)));

        Checkpoint loaded = saver.get(RunnableConfig.builder().threadId("exec-g").build()).orElseThrow();
        Object restored = loaded.getState().get("searchResults");
        assertTrue(restored instanceof Map, "应还原为 Map");

        List<?> results = (List<?>) ((Map<?, ?>) restored).get("results");
        assertEquals("a", ((Map<?, ?>) results.get(0)).get("title"), "嵌套结构应完整保留");
    }

    /**
     * 故意不实现 Serializable 的 Map，模拟框架内不可序列化的状态容器。
     * 注：Gson 2.11 的 LinkedTreeMap 已实现 Serializable，但仍保留归一化兜底
     * 以防御未来版本或其它 json 库引入不可序列化容器。
     */
    static class NonSerializableMap implements Map<String, Object> {
        private final Map<String, Object> delegate = new LinkedHashMap<>();

        public int size() { return delegate.size(); }
        public boolean isEmpty() { return delegate.isEmpty(); }
        public boolean containsKey(Object key) { return delegate.containsKey(key); }
        public boolean containsValue(Object value) { return delegate.containsValue(value); }
        public Object get(Object key) { return delegate.get(key); }
        public Object put(String key, Object value) { return delegate.put(key, value); }
        public Object remove(Object key) { return delegate.remove(key); }
        public void putAll(Map<? extends String, ?> m) { delegate.putAll(m); }
        public void clear() { delegate.clear(); }
        public Set<String> keySet() { return delegate.keySet(); }
        public Collection<Object> values() { return delegate.values(); }
        public Set<Entry<String, Object>> entrySet() { return delegate.entrySet(); }
    }

    @Test
    @DisplayName("不可序列化的 Map 值被归一化为 LinkedHashMap，续跑不炸")
    void toSerializable_normalizesNonSerializableMap() {
        NonSerializableMap raw = new NonSerializableMap();
        raw.put("results", List.of(Map.of("title", "a"), Map.of("title", "b")));
        assertFalse(raw instanceof Serializable, "测试前置：该 Map 不应可序列化");

        Object normalized = PortableCheckpointSaver.toSerializable(raw);
        assertTrue(normalized instanceof LinkedHashMap, "应归一化为 LinkedHashMap");
        List<?> results = (List<?>) ((Map<?, ?>) normalized).get("results");
        assertEquals("a", ((Map<?, ?>) results.get(0)).get("title"), "嵌套结构应完整保留");
    }

    static class TestPojo implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String value;

        TestPojo(String value) {
            this.value = value;
        }
    }

    @Test
    @DisplayName("领域 POJO 保持原类型，续跑时按原类型还原")
    void putPojo_preservesExactType() throws Exception {
        List<TestPojo> pojos = new ArrayList<>();
        pojos.add(new TestPojo("x"));

        saver.put(RunnableConfig.builder().threadId("exec-p").build(),
                checkpoint("cp-p", "first", "second", Map.of("items", pojos)));

        Checkpoint loaded = saver.get(RunnableConfig.builder().threadId("exec-p").build()).orElseThrow();
        List<?> items = (List<?>) loaded.getState().get("items");
        assertEquals(TestPojo.class, items.get(0).getClass(), "POJO 应原类型还原");
        assertEquals("x", ((TestPojo) items.get(0)).value);
    }
}
