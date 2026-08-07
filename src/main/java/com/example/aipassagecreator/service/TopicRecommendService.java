package com.example.aipassagecreator.service;

import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.model.dto.skill.TopicOption;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.TopicRecommendVO;
import com.example.aipassagecreator.skill.SkillExecution;
import com.example.aipassagecreator.skill.SkillRegistry;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 推荐选题服务 — 混合来源：平台热门 + 用户历史（RAG）+ AI 动态生成（topic-gen skill）
 *
 * <p>成本控制：AI 生成仅在 refresh=true 且未限流时触发；结果缓存 10 分钟；
 * AI 失败静默降级为热门+历史，不影响主流程。</p>
 */
@Slf4j
@Service
public class TopicRecommendService {

    /** AI 生成结果缓存（含时间戳） */
    private record AiCache(List<String> topics, Instant createdAt) {}

    /** 限流：每分钟最多触发的 AI 生成次数 */
    private static final int RATE_LIMIT_PER_MINUTE = 2;

    private final ArticleMapper articleMapper;
    private final RagService ragService;
    private final SkillRegistry skillRegistry;

    /** 是否启用 AI 动态生成（可用 LANGSEARCH_API_KEY 时才有意义） */
    @Value("${topic-recommend.ai-enabled:true}")
    private boolean aiEnabled;

    private final Map<String, AiCache> aiCacheMap = new ConcurrentHashMap<>();
    private final Map<String, List<Instant>> aiCallTimestamps = new ConcurrentHashMap<>();

    public TopicRecommendService(ArticleMapper articleMapper, RagService ragService,
                                 SkillRegistry skillRegistry) {
        this.articleMapper = articleMapper;
        this.ragService = ragService;
        this.skillRegistry = skillRegistry;
    }

    /**
     * 生成推荐选题
     *
     * @param refresh  是否触发 AI 新鲜选题（否则只返回热门+历史）
     * @param loginUser 当前登录用户
     */
    public TopicRecommendVO recommend(boolean refresh, User loginUser) {
        List<TopicRecommendVO.Item> items = new ArrayList<>();

        // 1. 平台热门（零成本，真实数据）
        List<String> hotTopics = loadHotTopics(8);
        hotTopics.forEach(t -> items.add(new TopicRecommendVO.Item(t, "hot")));

        // 2. 用户历史（RAG 检索，个性化）
        List<String> historyTopics = loadHistoryTopics(loginUser, 5);
        historyTopics.stream()
                .filter(t -> !hotTopics.contains(t))
                .forEach(t -> items.add(new TopicRecommendVO.Item(t, "history")));

        // 3. AI 动态生成（仅 refresh=true 且未限流；失败降级）
        boolean hasAi = false;
        if (refresh && aiEnabled && canTriggerAi(loginUser.getId())) {
            List<String> aiTopics = loadAiTopics(loginUser);
            if (!aiTopics.isEmpty()) {
                for (String t : aiTopics) {
                    items.add(new TopicRecommendVO.Item(t, "ai"));
                }
                hasAi = true;
            }
        }

        // 归一化（统一长短） + 去重（保序）
        items.forEach(i -> i.setText(normalizeTopic(i.getText())));
        List<TopicRecommendVO.Item> deduped = dedupe(items);

        return new TopicRecommendVO(deduped, hasAi);
    }

    /** 平台热门选题（按 topic 出现次数统计） */
    private List<String> loadHotTopics(int limit) {
        try {
            List<Map<String, Object>> rows = articleMapper.countTopicsByPopularity(limit);
            List<String> topics = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Object topic = row.get("topic");
                if (topic != null && !String.valueOf(topic).isBlank()) {
                    topics.add(String.valueOf(topic));
                }
            }
            return topics;
        } catch (Exception e) {
            log.warn("平台热门选题加载失败，降级为空", e);
            return List.of();
        }
    }

    /** 用户历史选题（RAG 语义检索该用户已完成文章） */
    private List<String> loadHistoryTopics(User loginUser, int limit) {
        try {
            boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
            Long userId = isAdmin ? null : loginUser.getId();
            // 以用户最近一篇已完成文章的标题/选题作为检索 query（比固定短语更贴近真实内容，
            // 避免语义检索命中任意无关 chunk）
            String query = recentArticleQuery(userId);
            if (query == null) {
                return List.of();
            }
            List<RagService.RagHit> hits = ragService.search(query, "article", userId, limit);
            List<String> topics = new ArrayList<>();
            for (RagService.RagHit hit : hits) {
                String title = hit.title();
                if (title != null && !title.isBlank() && !topics.contains(title)) {
                    topics.add(title);
                }
            }
            return topics;
        } catch (Exception e) {
            log.warn("用户历史选题加载失败，降级为空", e);
            return List.of();
        }
    }

    /** 取用户（admin 取全站）最近一篇已完成文章的标题/选题作为检索 query */
    @SuppressWarnings("rawtypes")
    private String recentArticleQuery(Long userId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .eq(Article::getIsDelete, 0)
                .eq(Article::getStatus, "COMPLETED")
                .orderBy(Article::getCreateTime, false)
                .limit(1);
        if (userId != null) {
            wrapper.eq(Article::getUserId, userId);
        }
        Article recent = articleMapper.selectOneByQuery(wrapper);
        if (recent == null) {
            return null;
        }
        if (recent.getMainTitle() != null && !recent.getMainTitle().isBlank()) {
            return recent.getMainTitle();
        }
        return recent.getTopic();
    }

    /** AI 动态生成（topic-gen skill 同步执行，结果缓存 10 分钟） */
    private List<String> loadAiTopics(User loginUser) {
        String cacheKey = String.valueOf(loginUser.getId());
        AiCache cached = aiCacheMap.get(cacheKey);
        if (cached != null && Duration.between(cached.createdAt(), Instant.now()).toMinutes() < 10) {
            log.info("AI 推荐选题命中缓存, userId={}", loginUser.getId());
            return cached.topics();
        }

        try {
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("direction", "生成当前互联网热门的自媒体选题，适合图文创作");
            inputs.put("style", "");

            SkillExecution execution = skillRegistry.createExecution("topic-gen", inputs);
            // 同步执行（无 SSE 回调），拿持久化输出
            Consumer<String> noop = s -> { };
            execution.execute(noop, loginUser.getId());
            Map<String, Object> output = execution.getPersistedOutput();

            List<TopicOption> options = parseOptions(output);
            List<String> topics = options.stream()
                    .map(TopicOption::getTitle)
                    .filter(t -> t != null && !t.isBlank())
                    .toList();

            if (!topics.isEmpty()) {
                aiCacheMap.put(cacheKey, new AiCache(topics, Instant.now()));
                log.info("AI 推荐选题生成成功, userId={}, 数量={}", loginUser.getId(), topics.size());
            }
            return topics;
        } catch (Exception e) {
            log.warn("AI 推荐选题生成失败（降级为空）, userId={}", loginUser.getId(), e);
            return List.of();
        }
    }

    /** 解析 topic-gen 输出（兼容直接 List 或包装在 outputData 里） */
    @SuppressWarnings("unchecked")
    private List<TopicOption> parseOptions(Map<String, Object> output) {
        Object raw = output.get("topicOptions");
        if (raw == null) {
            raw = output.get("output");
        }
        if (raw instanceof List) {
            return GsonUtils.fromJson(GsonUtils.toJson(raw), new TypeToken<List<TopicOption>>() {});
        }
        return List.of();
    }

    /** 限流检查：每分钟最多 RATE_LIMIT_PER_MINUTE 次 AI 触发 */
    private boolean canTriggerAi(Long userId) {
        String key = String.valueOf(userId);
        Instant now = Instant.now();
        List<Instant> timestamps = aiCallTimestamps.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (timestamps) {
            timestamps.removeIf(ts -> Duration.between(ts, now).toMinutes() >= 1);
            if (timestamps.size() >= RATE_LIMIT_PER_MINUTE) {
                log.info("AI 推荐选题触发限流, userId={}", userId);
                return false;
            }
            timestamps.add(now);
            return true;
        }
    }

    /** 去重（按 text 保序） */
    private List<TopicRecommendVO.Item> dedupe(List<TopicRecommendVO.Item> items) {
        List<TopicRecommendVO.Item> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (TopicRecommendVO.Item item : items) {
            if (seen.add(item.getText())) {
                result.add(item);
            }
        }
        return result;
    }

    /** 推荐选题文本上限（code point 数）。短选题原样保留，长标题截到语义断点。 */
    private static final int TOPIC_MAX_LEN = 20;

    /** 中文语义断点标点（按优先级），命中处截断语义最完整 */
    private static final String CN_PUNCT = "。；，、！？…：";
    /** 西文语义断点标点（按优先级） */
    private static final String EN_PUNCT = ".,;!? ";

    /**
     * 归一化选题文本：空白折叠 + 超长按语义断点截断（加省略号）。
     * <p>三个来源（热门/历史/AI）文本长短差异大，前端标签按内容撑开导致参差不齐；
     * 统一在此归一化，保证 API 层即输出长度一致的短选题。截断不硬切词——在
     * [3/4max, max] 区间找标点断点，找不到才兜底硬切。全程按 code point 处理，
     * 避免截断 emoji/surrogate pair。</p>
     */
    private String normalizeTopic(String s) {
        if (s == null) return "";
        // 空白折叠：trim + 内部连续空白压缩为单空格（AI 输出可能带换行/缩进）
        String text = s.trim().replaceAll("\\s+", " ");
        int count = text.codePointCount(0, text.length());
        if (count <= TOPIC_MAX_LEN) {
            return text;
        }
        int[] cps = text.codePoints().toArray();
        int cut = findBreakPoint(cps);
        return new String(cps, 0, cut) + "…";
    }

    /**
     * 在 [3/4*max, max] 区间内找最后一个语义断点（code point 索引）。
     * 优先中文标点（句读更接近语义边界），其次西文标点/空格。找不到返回 max（兜底硬切）。
     */
    private int findBreakPoint(int[] cps) {
        int max = TOPIC_MAX_LEN;
        int floor = max * 3 / 4;
        for (int i = max - 1; i >= floor; i--) {
            if (CN_PUNCT.indexOf(cps[i]) >= 0) {
                return i + 1; // 保留标点本身
            }
        }
        for (int i = max - 1; i >= floor; i--) {
            if (EN_PUNCT.indexOf(cps[i]) >= 0) {
                return i + 1;
            }
        }
        return max;
    }
}
