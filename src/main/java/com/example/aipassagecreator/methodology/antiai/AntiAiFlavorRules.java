package com.example.aipassagecreator.methodology.antiai;

import java.util.List;
import java.util.Map;

/**
 * 去AI味规则库：识别并替换 AI 写作的常见痕迹。
 * <p>规则来源：vibe-hub.org/anti-ai-flavor 及自媒体爆款实践经验。</p>
 *
 * <h3>七大规则维度</h3>
 * <ol>
 *   <li>AI 高频禁用词检测（"首先/其次/总的来说/值得注意的是"）</li>
 *   <li>句式模板化（"让我们来…"、"在…中/上/下"、"随着…的…"）</li>
 *   <li>过度修饰词（"非常/极其/更加/愈发/相当"）</li>
 *   <li>完美逻辑链（"因为…所以…从而…" 无断裂）</li>
 *   <li>缺乏个人视角（无"我"、"我觉得"、"但我发现"）</li>
 *   <li>情绪真空（无情感词、感叹、反问、口语化表达）</li>
 *   <li>段落节奏均匀（每段 3-5 句，无长短变化）</li>
 * </ol>
 */
public class AntiAiFlavorRules {

    private AntiAiFlavorRules() {}

    // ============== 1. AI 高频禁用词 ==============
    public static final List<String> AI_FORBIDDEN_WORDS = List.of(
            "首先", "其次", "最后", "总的来说", "总而言之", "综上所述",
            "值得注意的是", "需要指出的是", "不可否认的是",
            "毋庸置疑", "众所周知", "从某种意义上说",
            "在一定程度上", "在某种程度上",
            "从某个角度来说", "从某种程度来看",
            "第一", "第二", "第三", // 用于列举的序数词
            "换言之", "换句话说", "也就是说",
            "正如前文所述", "如上所述", "如前所述",
            "这意味着", "这也就意味着",
            "基于此", "在此基础上", "在此背景下",
            "与此同时", "除此之外", "除此之外"
    );

    // ============== 2. 句式模板 ==============
    public static final List<String> AI_TEMPLATE_PATTERNS = List.of(
            "让我们来", "让我们先", "让我们一起",
            "在我们的日常生活中",
            "在当今社会", "在当今时代", "在当下",
            "随着社会的", "随着科技的", "随着时代的",
            "随着互联网的", "随着人工智能的",
            "在…中起着", "在…中扮演着",
            "对…产生了深远的影响",
            "引起了广泛的关注", "引发热议",
            "被广泛认为是", "被普遍认为是"
    );

    // ============== 3. 过度修饰词 ==============
    public static final List<String> AI_OVERUSED_ADVERBS = List.of(
            "非常", "极其", "十分", "相当", "异常",
            "更加", "愈发", "越来越", "进一步",
            "特别", "格外", "尤为", "颇为",
            "彻底", "完全", "绝对", "一定", "必定",
            "确实", "的确", "显然", "显然地",
            "毫无疑问", "毫无疑义",
            "实际上", "事实上", "本质上",
            "真正地", "真正意义上的"
    );

    // ============== 4. 替换建议 ==============
    public static final Map<String, String> REPLACEMENT_MAP = Map.ofEntries(
            Map.entry("首先", "▁"),
            Map.entry("其次", "▁"),
            Map.entry("总的来说", "说白了"),
            Map.entry("总而言之", "一句话"),
            Map.entry("综上所述", "捋一下"),
            Map.entry("值得注意的是", "说个有意思的"),
            Map.entry("毋庸置疑", "明摆着"),
            Map.entry("众所周知", "大伙儿都知道"),
            Map.entry("从某种意义上说", "换句话讲"),
            Map.entry("在一定程度上", "多少有点"),
            Map.entry("与此同时", "这边…那边…"),
            Map.entry("除此之外", "对了"),
            Map.entry("非常", "特别（慎用）/删掉"),
            Map.entry("极其", "很/删掉"),
            Map.entry("显然", "明摆着"),
            Map.entry("事实上", "其实"),
            Map.entry("然而", "但/可是"),
            Map.entry("因此", "所以/那"),
            Map.entry("此外", "另外/还有"),
            Map.entry("例如", "比如/像"),
            Map.entry("导致", "让/搞到"),
            Map.entry("参与", "加入/进场"),
            Map.entry("并且", "还/而且"),
            Map.entry("关于", "说到/聊到"),
            Map.entry("认为", "觉得/感觉"),
            Map.entry("获得", "拿到/搞到"),
            Map.entry("进行", "做/搞/直接删掉"),
            Map.entry("通过", "靠/用/借着"),
            Map.entry("对于", "对/之于"),
            Map.entry("作为", "当/身为"),
            Map.entry("成为", "变成/混成"),
            Map.entry("拥有", "有/手里攥着"),
            Map.entry("表示", "说/认为"),
            Map.entry("建议", "劝/建议（少用）"),
            Map.entry("需要", "得/要"),
            Map.entry("能够", "能/可以"),
            Map.entry("应该", "该/得"),
            Map.entry("已经", "已经/早"),
            Map.entry("经过", "通过/过"),
            Map.entry("开始", "开整/上手"),
            Map.entry("结束", "收工/完事儿"),
            Map.entry("帮助", "帮/拉一把"),
            Map.entry("增加", "加/涨"),
            Map.entry("减少", "减/降"),
            Map.entry("提高", "提/拉高"),
            Map.entry("降低", "降/压低"),
            Map.entry("实现", "做成/搞定"),
            Map.entry("利用", "用/拿"),
            Map.entry("影响", "带偏/撼动"),
            Map.entry("选择", "选/挑"),
            Map.entry("坚持", "死磕/熬"),
            Map.entry("努力", "拼/使劲儿"),
            Map.entry("重要", "关键/要命"),
            Map.entry("优秀", "牛/顶/绝"),
            Map.entry("美丽", "好看/漂亮/绝了"),
            Map.entry("喜欢", "爱/上头"),
            Map.entry("理解", "懂/明白"),
            Map.entry("知道", "晓得/清楚")
    );

    // ============== 5. 创作引导 Prompt 段 ==============

    /** 标题生成去AI味引导 */
    public static final String TITLE_GUIDANCE = """
            【去AI味要求】
            标题必须避免以下AI常见套路：
            - 不用"揭秘"、"干货"、"必看"、"建议收藏"等烂大街词
            - 不用"如何…"、"…的方法"、"…的技巧"等模板句式
            - 标题要有态度、有情绪、有具体数字
            - 好标题 = 具体场景 + 反常识/痛点 + 有情绪
            - 例（好）："写了3个月小红书，我才发现爆款靠的不是努力"
            - 例（差）："如何提高小红书写作效率的几种方法"
            """;

    /** 正文创作去AI味引导 */
    public static final String CONTENT_GUIDANCE = """
            【去AI味写作要求】
            请严格遵循以下规则，让文章读起来像真人写的：

            ❌ 避免的AI词：
            - 首先/其次/最后 / 总的来说/总而言之 / 值得注意的是
            - 在…中/上/下 / 随着…的… / 对…产生了深远影响
            - 非常/极其/十分/相当 / 显然/毫无疑问/毋庸置疑
            - 导致/因此/从而/并且 / 例如/此外/与此同时

            ✅ 替换为口语化表达：
            - "但"替代"然而"；"所以"替代"因此"；"其实"替代"事实上"
            - "说白了"替代"总的来说"；"比如"替代"例如"
            - "说个有意思的"替代"值得注意的是"
            - "我"替代"作者"或省略

            ✅ 段落节奏：
            - 每段不超过 3-5 行，长短交替
            - 用短句开头，偶尔用感叹句或反问
            - 加入个人视角（"我"、"我见过"、"我试过"、"但我发现"）
            - 有情绪：共鸣、愤怒、惊喜、怀疑——不要"理性客观"

            ✅ 让文章"活"起来：
            - 加入具体场景和细节，不说空话
            - 用具体数字替代模糊描述
            - 有观点、有态度、有立场
            - 结尾要"扎心"或"爽"，不要"希望本文对你有帮助"
            """;

    /** 改写去AI味指令 */
    public static final String REWRITE_INSTRUCTION = """
            请对以下文章进行"去AI味"改写。目标是让文章读起来像真人写的，而不是AI生成的。

            改写规则：
            1. 删除所有AI高频词汇（首先/其次/总的来说/值得注意的是/毋庸置疑/由此可见等）
            2. 把"导致/因此/从而"替换为"让/所以/那"
            3. 把"例如/此外"替换为"比如/还有"
            4. 加入个人视角：用"我"、"我见过"、"我试过"
            5. 加入情感词和口语化表达
            6. 打乱段落节奏：长短交替，不要每段3-5句
            7. 删除过度修饰词（非常/极其/十分/相当）
            8. 每个段落只传达一个核心信息
            9. 开头要有钩子，结尾要有情绪
            10. 读一遍：像不像真人说出来的话？
            """;

    // ============== 检测方法 ==============

    /**
     * 检测文本中的AI味指标。
     * @return 违规项列表，格式 "[规则名] 违规词/句式: ..."
     */
    public static List<String> detect(String text) {
        if (text == null || text.isBlank()) return List.of();
        java.util.List<String> violations = new java.util.ArrayList<>();

        // 规则1：AI禁用词
        for (String word : AI_FORBIDDEN_WORDS) {
            if (text.contains(word)) {
                violations.add("[AI禁用词] 发现: 「" + word + "」");
            }
        }
        // 规则2：模板句式
        for (String pattern : AI_TEMPLATE_PATTERNS) {
            if (text.contains(pattern)) {
                violations.add("[句式模板] 发现: 「" + pattern + "」");
            }
        }
        // 规则3：过度修饰
        for (String adv : AI_OVERUSED_ADVERBS) {
            if (text.contains(adv)) {
                violations.add("[过度修饰] 发现: 「" + adv + "」");
            }
        }
        // 规则4：缺少个人视角（"我"）
        if (!text.contains("我") && !text.contains("我见过") && !text.contains("我试过")) {
            violations.add("[缺个人视角] 全文无「我」——建议加入个人经历");
        }
        // 规则5：段落节奏均匀（每段字数相近）
        checkParagraphRhythm(text, violations);

        return violations;
    }

    private static void checkParagraphRhythm(String text, List<String> violations) {
        String[] paragraphs = text.split("\n\n");
        if (paragraphs.length < 3) return;
        int[] lengths = new int[paragraphs.length];
        for (int i = 0; i < paragraphs.length; i++) {
            lengths[i] = paragraphs[i].replaceAll("\\s", "").length();
        }
        // 检查连续段落字数是否过于均匀（差异 < 30% 视为节奏单一）
        int uniformCount = 0;
        for (int i = 1; i < lengths.length; i++) {
            int max = Math.max(lengths[i], lengths[i - 1]);
            int min = Math.min(lengths[i], lengths[i - 1]);
            if (max > 0 && (double) min / max > 0.7) {
                uniformCount++;
            }
        }
        if (uniformCount >= lengths.length - 1) {
            violations.add("[段落节奏] 段落字数过于均匀——建议改为长短交替");
        }
    }
}