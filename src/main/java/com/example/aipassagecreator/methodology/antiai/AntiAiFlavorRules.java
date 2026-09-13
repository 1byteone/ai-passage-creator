package com.example.aipassagecreator.methodology.antiai;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 表达质量规则库：识别影响自然度、清晰度和可信度的常见写作模式。
 * <p>规则来源：vibe-hub.org/anti-ai-flavor 及自媒体爆款实践经验。</p>
 *
 * <h3>内容质量规则维度</h3>
 * <ol>
 *   <li>AI 高频禁用词检测（"首先/其次/总的来说/值得注意的是"）</li>
 *   <li>句式模板化（"让我们来…"、"在…中/上/下"、"随着…的…"）</li>
 *   <li>过度修饰词（"非常/极其/更加/愈发/相当"）</li>
 *   <li>完美逻辑链（"因为…所以…从而…" 无断裂）</li>
 *   <li>模糊归因、聊天式客套、免责声明和空泛结论</li>
 *   <li>机械排比、格式过度和段落节奏均匀</li>
 * </ol>
 */
public class AntiAiFlavorRules {

    private AntiAiFlavorRules() {}

    /** Humanizer-zh 来源版本：只吸收规则与编辑原则，不运行外部仓库代码。 */
    public static final String HUMANIZER_SOURCE_REPOSITORY =
            "https://github.com/op7418/Humanizer-zh";
    public static final String HUMANIZER_SOURCE_COMMIT = "91f3d394db8419c20d67ebe22a96cf8fee0a404b";
    public static final String HUMANIZER_SOURCE_LICENSE = "MIT";
    public static final String HUMANIZER_RULE_VERSION = "humanizer-zh@91f3d394";

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
            "与此同时", "除此之外", "除此之外",
            // Humanizer-zh：高频 AI 词汇
            "至关重要", "深入探讨", "持久的", "培养", "相互作用",
            "复杂性", "格局", "织锦", "宝贵的", "充满活力的",
            "彰显", "凸显", "不可磨灭的印记"
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
            "真正地", "真正意义上的",
            // Humanizer-zh：宣传性和广告式修饰
            "无缝", "直观", "强大", "令人叹为观止", "开创性的",
            "著名的", "必游之地", "迷人的", "自然之美"
    );

    /** Humanizer-zh：模糊归因、协作话术、免责声明和空泛结论。 */
    public static final List<String> HUMANIZER_VAGUE_ATTRIBUTION_PATTERNS = List.of(
            "行业报告显示", "观察者指出", "专家认为", "一些批评者认为",
            "多个来源", "多个出版物", "广泛认为"
    );
    public static final List<String> HUMANIZER_CONVERSATIONAL_PATTERNS = List.of(
            "希望这对您有帮助", "您说得完全正确", "请告诉我",
            "这是个好问题", "如果您想让我", "我很乐意为您"
    );
    public static final List<String> HUMANIZER_DISCLAIMER_PATTERNS = List.of(
            "根据我最后的训练", "我的知识截止于", "我的知识更新至",
            "基于可用信息", "现成资料中没有广泛记录"
    );
    public static final List<String> HUMANIZER_GENERIC_CONCLUSION_PATTERNS = List.of(
            "未来看起来光明", "激动人心的时代即将到来", "追求卓越的旅程",
            "向正确方向迈出的重要一步", "继续蓬勃发展"
    );

    private static final Pattern NOT_ONLY_PATTERN =
            Pattern.compile("不仅(?:仅是|只是)?[^。！？\\n]{0,40}(?:而且|还|更|而是)");
    private static final Pattern INLINE_HEADING_LIST_PATTERN =
            Pattern.compile("(?m)^\\s*[-*]\\s+\\*\\*[^*\\n]+\\*\\*\\s*[：:]");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*[^*\\n]+\\*\\*");

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

    /** 标题生成质量引导 */
    public static final String TITLE_GUIDANCE = """
            【标题表达质量要求】
            标题必须避免以下常见模板和空泛修饰：
            - 不用"揭秘"、"干货"、"必看"、"建议收藏"等烂大街词
            - 不用"如何…"、"…的方法"、"…的技巧"等模板句式
            - 标题要有态度、有情绪、有具体数字
            - 好标题 = 具体场景 + 反常识/痛点 + 有情绪
            - 例（好）："写了3个月小红书，我才发现爆款靠的不是努力"
            - 例（差）："如何提高小红书写作效率的几种方法"
            """;

    /** 正文创作质量引导 */
    public static final String CONTENT_GUIDANCE = """
            【正文表达质量要求】
            请严格遵循以下规则，让文章清晰、准确、自然，并符合目标读者和文章体裁：

            避免空泛、夸大和模板化表达：
            - 首先/其次/最后 / 总的来说/总而言之 / 值得注意的是
            - 在…中/上/下 / 随着…的… / 对…产生了深远影响
            - 非常/极其/十分/相当 / 显然/毫无疑问/毋庸置疑
            - 导致/因此/从而/并且 / 例如/此外/与此同时

            表达与结构：
            - 优先使用准确的动词、名词和具体细节；能删掉的修饰词直接删掉
            - 只保留确有逻辑作用的因果、转折和递进连接词
            - 混合长短句和段落长度，但不刻意制造残句、感叹句或反问句
            - 是否使用第一人称取决于文章体裁和原文事实，不强制加入个人经历
            - 保持正式度、专业性、立场和目标读者，不为追求口语化而牺牲准确性
            """;

    /** 改写质量指令 */
    public static final String REWRITE_INSTRUCTION = """
            请对以下文章进行内容质量改写。目标是让表达更准确、清晰、自然，而不是伪装作者身份或绕过检测器。

            改写规则：
            1. 删除没有信息增量的开场、总结、宣传性形容词和机械连接词
            2. 使用具体、准确的动词和名词，必要时保留专业术语
            3. 合并重复句式，调整段落节奏，但不刻意添加残句、口语或情绪
            4. 只保留原文已有的事实、来源、数字、案例、立场和限定条件
            5. 不加入个人经历、引用、数据、观点或无法核验的来源
            6. 保留原文 Markdown 结构和信息层级
            """;

    /**
     * Humanizer-zh 的生成阶段约束。
     * <p>它是编辑质量标准，不要求每篇文章都使用第一人称，也不允许为了“像真人”
     * 凭空添加经历、数据、引用或事实。</p>
     */
    public static final String HUMANIZER_GENERATION_GUIDANCE = """

            【Humanizer-zh 写作质量标准】
            本段要求用于提升自然度、清晰度和可信度，不以绕过任何检测器为目标。
            - 直接陈述事实，删掉空泛的开场、总结和宣传性形容词。
            - 少用“作为……的证明”“标志着”“彰显”“不断演变的格局”等夸大意义的表达。
            - 不使用“专家认为”“行业报告显示”等模糊归因；需要归因时给出具体来源，无法确认就不要编造。
            - 避免机械的“不仅……而且……”、三项排比、连续相同句式和过密连接词。
            - 混合长短句，段落长短自然变化；破折号、粗体、列表只在确有信息价值时使用。
            - 保留原文事实、限定条件和 Markdown 结构，不擅自加入个人经历、数字、案例或观点。
            - 适当表达判断和不确定性，但不要用空泛的乐观结尾或聊天式客套话收尾。
            """;

    /** 完整正文生成质量标准，供不同正文生成入口复用。 */
    public static final String CONTENT_QUALITY_GUIDANCE =
            CONTENT_GUIDANCE + HUMANIZER_GENERATION_GUIDANCE;

    // ============== 检测方法 ==============

    /**
     * 检测文本中的表达质量风险。
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
        detectHumanizerPatterns(text, violations);
        // 规则4：段落节奏均匀（每段字数相近）
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

    private static void detectHumanizerPatterns(String text, List<String> violations) {
        for (String pattern : HUMANIZER_VAGUE_ATTRIBUTION_PATTERNS) {
            if (text.contains(pattern)) {
                violations.add("[模糊归因] 发现: 「" + pattern + "」");
            }
        }
        for (String pattern : HUMANIZER_CONVERSATIONAL_PATTERNS) {
            if (text.contains(pattern)) {
                violations.add("[协作话术] 发现: 「" + pattern + "」");
            }
        }
        for (String pattern : HUMANIZER_DISCLAIMER_PATTERNS) {
            if (text.contains(pattern)) {
                violations.add("[免责声明] 发现: 「" + pattern + "」");
            }
        }
        for (String pattern : HUMANIZER_GENERIC_CONCLUSION_PATTERNS) {
            if (text.contains(pattern)) {
                violations.add("[空泛结论] 发现: 「" + pattern + "」");
            }
        }
        if (NOT_ONLY_PATTERN.matcher(text).find()) {
            violations.add("[否定式排比] 发现「不仅……而且/还/更/而是」结构");
        }
        if (countDashes(text) >= 3) {
            violations.add("[破折号过度] 破折号使用次数过多——建议改用句号或逗号");
        }
        if (BOLD_PATTERN.matcher(text).results().count() >= 5) {
            violations.add("[粗体过度] 粗体强调过密——只保留真正需要强调的内容");
        }
        if (INLINE_HEADING_LIST_PATTERN.matcher(text).results().count() >= 2) {
            violations.add("[内联标题列表] 多个列表项使用“粗体标题：解释”模板");
        }
        if (containsEmoji(text)) {
            violations.add("[表情符号] 正文含装饰性表情符号——按正式度删除或减少");
        }
    }

    private static long countDashes(String text) {
        return text.chars().filter(c -> c == '—' || c == '–').count();
    }

    private static boolean containsEmoji(String text) {
        return text.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x1F300 && codePoint <= 0x1FAFF)
                        || (codePoint >= 0x2600 && codePoint <= 0x27BF));
    }
}
