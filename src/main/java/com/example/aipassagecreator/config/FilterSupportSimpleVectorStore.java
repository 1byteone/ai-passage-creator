package com.example.aipassagecreator.config;

import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStoreContent;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import java.util.List;
import java.util.Map;

/**
 * 支持按 Filter 表达式删除的内存向量库。
 *
 * <p>Spring AI 的 {@link SimpleVectorStore} 只实现了按 ID 列表删除
 * {@code doDelete(List<String>)}，未覆写按过滤器删除 {@code doDelete(Filter.Expression)}——
 * 基类默认实现直接抛 {@code UnsupportedOperationException}，导致内存降级模式下
 * {@code deleteByTaskId / deleteBySource} 全部静默失效（重复索引清旧向量、文章删除清向量
 * 都变成 no-op，向量持续累积，已删内容仍被检索命中）。</p>
 *
 * <p>本子类覆写 {@code doDelete(Filter.Expression)}：把 Filter AST 对每个向量的 metadata
 * 求值，收集命中 ID 后走 {@code doDelete(List)}，使内存模式与 pgvector 生产路径行为一致。
 * 内存模式本无持久化需求，补齐删除语义即可与生产对齐。</p>
 */
public class FilterSupportSimpleVectorStore extends SimpleVectorStore {

    public FilterSupportSimpleVectorStore(SimpleVectorStoreBuilder builder) {
        super(builder);
    }

    @Override
    protected void doDelete(Expression filter) {
        List<String> ids = store.values().stream()
                .filter(content -> matches(content, filter))
                .map(SimpleVectorStoreContent::getId)
                .toList();
        if (!ids.isEmpty()) {
            doDelete(ids);
        }
    }

    /** 当前向量条数（供测试断言幂等/删除生效） */
    public int size() {
        return store.size();
    }

    /** 对单个向量的 metadata 求值 Filter 表达式（与 pgvector 的 FilterExpressionConverter 语义对齐） */
    private boolean matches(SimpleVectorStoreContent content, Expression expr) {
        Map<String, Object> metadata = content.getMetadata();
        return switch (expr.type()) {
            case AND -> matches(content, (Expression) expr.left()) && matches(content, (Expression) expr.right());
            case OR -> matches(content, (Expression) expr.left()) || matches(content, (Expression) expr.right());
            case NOT -> !matches(content, (Expression) expr.left());
            case EQ -> compareEq(metadata, (Key) expr.left(), (Value) expr.right());
            case NE -> !compareEq(metadata, (Key) expr.left(), (Value) expr.right());
            case GT -> ordered(metadata, (Key) expr.left(), (Value) expr.right(), c -> c > 0);
            case GTE -> ordered(metadata, (Key) expr.left(), (Value) expr.right(), c -> c >= 0);
            case LT -> ordered(metadata, (Key) expr.left(), (Value) expr.right(), c -> c < 0);
            case LTE -> ordered(metadata, (Key) expr.left(), (Value) expr.right(), c -> c <= 0);
            case IN -> containsValue(metadata, (Key) expr.left(), (Value) expr.right());
            case NIN -> !containsValue(metadata, (Key) expr.left(), (Value) expr.right());
        };
    }

    private boolean compareEq(Map<String, Object> metadata, Key key, Value value) {
        Object actual = metadata.get(key.key());
        return actual != null && actual.equals(value.value());
    }

    private boolean containsValue(Map<String, Object> metadata, Key key, Value value) {
        Object actual = metadata.get(key.key());
        if (actual == null) {
            return false;
        }
        if (value.value() instanceof Iterable<?> iterable) {
            for (Object v : iterable) {
                if (actual.equals(v)) {
                    return true;
                }
            }
            return false;
        }
        return actual.equals(value.value());
    }

    /** 有序比较：类型不匹配或值缺失时视为不匹配（返回 false），避免误判 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean ordered(Map<String, Object> metadata, Key key, Value value,
                            java.util.function.IntPredicate predicate) {
        Object actual = metadata.get(key.key());
        Object target = value.value();
        if (actual instanceof Comparable comparable && target != null) {
            try {
                return predicate.test(comparable.compareTo(target));
            } catch (ClassCastException e) {
                return false;
            }
        }
        return false;
    }
}
