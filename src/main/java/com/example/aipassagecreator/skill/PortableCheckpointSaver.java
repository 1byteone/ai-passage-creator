package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 可移植的 CheckpointSaver — 将 HITL 检查点持久化到数据库（H2/MySQL 通用）。
 * <p>
 * 替代进程内 {@code MemorySaver}：应用重启后仍可从检查点续跑待确认的 Skill 执行。
 * 语义与 MemorySaver 完全一致（本实现已按字节码核对）：
 * <ul>
 *   <li>get：config 带 checkPointId → 精确命中；否则取该线程最新检查点（id 最大的行）</li>
 *   <li>put：config 带 checkPointId → 原地更新；否则插入新行并返回携带该 id 的 config</li>
 *   <li>release：删除该线程全部检查点（框架当前不主动调用，保留语义完整性）</li>
 * </ul>
 * <p>
 * 状态 Map 用 Java 序列化保存以保留类型精确性（续跑时图节点按原类型 cast）。
 * 仅 Gson {@code json} 解析产物（不可序列化的 {@code LinkedTreeMap}）会被深拷贝为
 * {@link LinkedHashMap} / {@link ArrayList}，领域 POJO 保持原类型。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortableCheckpointSaver implements BaseCheckpointSaver {

    private static final String DEFAULT_THREAD_ID = "$default";

    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_COLUMNS =
            "select checkpoint_id, node_id, next_node_id, state_data from skill_checkpoint";

    private final RowMapper<Checkpoint> checkpointMapper = (rs, rowNum) -> Checkpoint.builder()
            .id(rs.getString("checkpoint_id"))
            .nodeId(rs.getString("node_id"))
            .nextNodeId(rs.getString("next_node_id"))
            .state(deserialize(rs.getBytes("state_data")))
            .build();

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        return jdbcTemplate.query(
                SELECT_COLUMNS + " where thread_id = ? order by id desc",
                checkpointMapper, threadId(config));
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        Optional<String> checkpointId = config.checkPointId();
        String sql = checkpointId.isPresent()
                ? SELECT_COLUMNS + " where thread_id = ? and checkpoint_id = ? order by id desc limit 1"
                : SELECT_COLUMNS + " where thread_id = ? order by id desc limit 1";
        List<Checkpoint> checkpoints = checkpointId.isPresent()
                ? jdbcTemplate.query(sql, checkpointMapper, threadId(config), checkpointId.get())
                : jdbcTemplate.query(sql, checkpointMapper, threadId(config));
        return checkpoints.isEmpty() ? Optional.empty() : Optional.of(checkpoints.get(0));
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        String threadId = threadId(config);
        byte[] stateData = serialize(checkpoint.getState());
        Optional<String> checkpointId = config.checkPointId();

        if (checkpointId.isPresent()) {
            int affected = jdbcTemplate.update(
                    "update skill_checkpoint set node_id = ?, next_node_id = ?, state_data = ?, update_time = now() "
                            + "where thread_id = ? and checkpoint_id = ?",
                    checkpoint.getNodeId(), checkpoint.getNextNodeId(), stateData, threadId, checkpointId.get());
            if (affected == 0) {
                // config 引用了尚未落库的检查点 id，兜底插入（等价于 MemorySaver 的原地替换语义）
                jdbcTemplate.update(
                        "insert into skill_checkpoint (thread_id, checkpoint_id, node_id, next_node_id, state_data) "
                                + "values (?, ?, ?, ?, ?)",
                        threadId, checkpointId.get(), checkpoint.getNodeId(),
                        checkpoint.getNextNodeId(), stateData);
            }
            return config;
        }

        jdbcTemplate.update(
                "insert into skill_checkpoint (thread_id, checkpoint_id, node_id, next_node_id, state_data) "
                        + "values (?, ?, ?, ?, ?)",
                threadId, checkpoint.getId(), checkpoint.getNodeId(),
                checkpoint.getNextNodeId(), stateData);
        return RunnableConfig.builder(config).checkPointId(checkpoint.getId()).build();
    }

    @Override
    public Tag release(RunnableConfig config) {
        String threadId = threadId(config);
        Collection<Checkpoint> checkpoints = list(config);
        jdbcTemplate.update("delete from skill_checkpoint where thread_id = ?", threadId);
        return new Tag(threadId, checkpoints);
    }

    private String threadId(RunnableConfig config) {
        return config.threadId().orElse(DEFAULT_THREAD_ID);
    }

    static byte[] serialize(Map<String, Object> state) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(toSerializable(state));
        }
        return bos.toByteArray();
    }

    static Map<String, Object> deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            return Map.of();
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            Object obj = ois.readObject();
            if (obj instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) map;
                return result;
            }
            log.warn("检查点状态反序列化结果非 Map，已按空状态处理");
            return Map.of();
        } catch (IOException | ClassNotFoundException e) {
            log.error("检查点状态反序列化失败", e);
            return Map.of();
        }
    }

    /**
     * 深拷贝不可序列化值（Gson 的 LinkedTreeMap 等）为可序列化结构。
     * 已实现 Serializable 的值（String/POJO/Jackson 容器/byte[]）直接保留，
     * 保证续跑时按原类型还原。
     */
    static Object toSerializable(Object value) {
        if (value == null || value instanceof Serializable) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>(map.size());
            map.forEach((k, v) -> copy.put(k, toSerializable(v)));
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object item : collection) {
                copy.add(toSerializable(item));
            }
            return copy;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                copy.add(toSerializable(Array.get(value, i)));
            }
            return copy;
        }
        return value;
    }
}
