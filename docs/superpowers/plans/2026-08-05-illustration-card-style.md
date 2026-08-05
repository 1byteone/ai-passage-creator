# 插画人物卡片风格 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为图文卡片新增 `illustration` 风格，AI 动态生成插画人物作为封面主视觉，AI 失败自动熔断到静态素材库。

**Architecture:** 复用现有卡片管线。新增 `illustration/` 包（IllustrationCharacterStyle 枚举 + IllustrationPromptBuilder + StaticIllustrationLibrary + IllustrationImageService），style=illustration 时在 CardService 生成插画 URL，经 `CardImageResolver` base64 内联进 `illustration.html` 模板，走现有安全渲染管线（JS disabled）。

**Tech Stack:** Java 21 / Spring Boot 3.5 / MyBatis-Flex / Thymeleaf / Playwright / JUnit 5 + Mockito

## Global Constraints

- 卡片风格白名单由 `CardStyle` 枚举管理，未知风格回退 `WARM`
- 模板正文/标题一律 `th:text`（HTML 转义），禁止 `th:utext`
- 静态素材来自免费可商用库（unDraw 等），授权记录在 `resources/illustration/LICENSES.md`
- 渲染走现有 `render()` 安全管线（JS disabled + route abort），**不启用 JS**
- 配额复用现有 `quotaService`：AI 成功扣 1，失败熔断不扣
- 测试运行：`mvn test -Dspring.profiles.active=test`
- 提交格式：Conventional Commits + `Co-Authored-By: Claude <noreply@anthropic.com>`

---

### Task 1: IllustrationCharacterStyle 枚举 + 单元测试

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/card/illustration/IllustrationCharacterStyle.java`
- Create: `src/test/java/com/example/aipassagecreator/card/illustration/IllustrationCharacterStyleTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `IllustrationCharacterStyle` 枚举，含 `getName()` / `from(String)`（未知回退 `HEALING`）、4 个常量 HEALING/CUTE/DOODLE/WATERCOLOR

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.card.illustration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IllustrationCharacterStyleTest {

    @Test
    void from_knownNames_returnsEnum() {
        assertEquals(IllustrationCharacterStyle.HEALING, IllustrationCharacterStyle.from("healing"));
        assertEquals(IllustrationCharacterStyle.CUTE, IllustrationCharacterStyle.from("cute"));
        assertEquals(IllustrationCharacterStyle.DOODLE, IllustrationCharacterStyle.from("doodle"));
        assertEquals(IllustrationCharacterStyle.WATERCOLOR, IllustrationCharacterStyle.from("watercolor"));
    }

    @Test
    void from_nullOrBlank_returnsDefault() {
        assertEquals(IllustrationCharacterStyle.HEALING, IllustrationCharacterStyle.from(null));
        assertEquals(IllustrationCharacterStyle.HEALING, IllustrationCharacterStyle.from(""));
        assertEquals(IllustrationCharacterStyle.HEALING, IllustrationCharacterStyle.from("  "));
    }

    @Test
    void from_unknownName_returnsDefault() {
        assertEquals(IllustrationCharacterStyle.HEALING, IllustrationCharacterStyle.from("neon"));
    }

    @Test
    void getName_returnsKey() {
        assertEquals("healing", IllustrationCharacterStyle.HEALING.getName());
        assertEquals("watercolor", IllustrationCharacterStyle.WATERCOLOR.getName());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=IllustrationCharacterStyleTest`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现枚举**

```java
package com.example.aipassagecreator.card.illustration;

/**
 * 插画人物子风格枚举 — 白名单 + 默认值管理。
 * 与 {@link com.example.aipassagecreator.card.CardStyle} 同理，未知回退 HEALING。
 */
public enum IllustrationCharacterStyle {
    HEALING("healing"),
    CUTE("cute"),
    DOODLE("doodle"),
    WATERCOLOR("watercolor");

    private final String name;

    IllustrationCharacterStyle(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static IllustrationCharacterStyle from(String name) {
        if (name == null || name.isBlank()) {
            return HEALING;
        }
        for (IllustrationCharacterStyle style : values()) {
            if (style.name.equalsIgnoreCase(name)) {
                return style;
            }
        }
        return HEALING;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=IllustrationCharacterStyleTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/card/illustration/IllustrationCharacterStyle.java src/test/java/com/example/aipassagecreator/card/illustration/IllustrationCharacterStyleTest.java
git commit -m "feat(card): IllustrationCharacterStyle 子风格枚举 — healing/cute/doodle/watercolor 白名单

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: IllustrationPromptBuilder 提示词母版 + 单元测试

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/card/illustration/IllustrationPromptBuilder.java`
- Create: `src/test/java/com/example/aipassagecreator/card/illustration/IllustrationPromptBuilderTest.java`

**Interfaces:**
- Consumes: `IllustrationCharacterStyle`
- Produces: `String build(String mainTitle, IllustrationCharacterStyle style)` — 中文提示词母版；`String resolveCharacterDescription(String mainTitle)` — 主题→人物形象映射（内部方法，测试可见）

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.card.illustration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IllustrationPromptBuilderTest {

    private final IllustrationPromptBuilder builder = new IllustrationPromptBuilder();

    @Test
    void build_includesStyleVisualAndTheme() {
        String prompt = builder.build("如何高效学习", IllustrationCharacterStyle.HEALING);
        assertTrue(prompt.contains("书桌"));
        assertTrue(prompt.contains("暖米黄"));
        assertTrue(prompt.contains("标题区"));
        assertTrue(prompt.contains("无文字"));
    }

    @Test
    void build_differentStyles_differ() {
        String healing = builder.build("标题", IllustrationCharacterStyle.HEALING);
        String doodle = builder.build("标题", IllustrationCharacterStyle.DOODLE);
        assertNotEquals(healing, doodle);
        assertTrue(doodle.contains("马克笔"));
    }

    @Test
    void build_nullTitle_usesDefault() {
        String prompt = builder.build(null, IllustrationCharacterStyle.CUTE);
        assertTrue(prompt.contains("CUTE") || prompt.contains("可爱"));
    }

    @Test
    void resolveCharacterDescription_knowsTopics() {
        assertEquals("书桌", builder.resolveCharacterDescription("高效学习的方法"));
        assertEquals("茶", builder.resolveCharacterDescription("春季养生食谱"));
        assertEquals("城市", builder.resolveCharacterDescription("2025科技趋势"));
    }

    @Test
    void resolveCharacterDescription_unknownTopic_returnsDefault() {
        assertEquals("笑脸", builder.resolveCharacterDescription("随便一段文字"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=IllustrationPromptBuilderTest`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现 PromptBuilder**

```java
package com.example.aipassagecreator.card.illustration;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插画人物提示词母版组装器。
 * <p>母版固定框架 + 子风格变量 + 主题变量。主题变量仅取 mainTitle 纯文本，
 * 经关键词表映射为人物形象，不拼接用户自由文本（防 prompt injection）。</p>
 */
@Component
public class IllustrationPromptBuilder {

    /** 主题关键词 → 人物形象描述 */
    private static final Map<String, String> TOPIC_MAP = new LinkedHashMap<>();
    static {
        TOPIC_MAP.put("学习", "书桌前的学生");
        TOPIC_MAP.put("效率", "书桌前的学生");
        TOPIC_MAP.put("知识", "书桌前的学生");
        TOPIC_MAP.put("读书", "书桌前的学生");
        TOPIC_MAP.put("养生", "品茶的女性");
        TOPIC_MAP.put("健康", "晨练的年轻人");
        TOPIC_MAP.put("饮食", "做饭的厨师");
        TOPIC_MAP.put("美食", "做饭的厨师");
        TOPIC_MAP.put("科技", "用电脑的职场青年");
        TOPIC_MAP.put("互联网", "用电脑的职场青年");
        TOPIC_MAP.put("城市", "漫步城市的路人");
        TOPIC_MAP.put("旅行", "背包旅行者");
        TOPIC_MAP.put("情感", "温柔微笑的人物");
        TOPIC_MAP.put("成长", "微笑的年轻人");
        TOPIC_MAP.put("职场", "穿职业装的上班族");
        TOPIC_MAP.put("理财", "认真记账的青年");
        TOPIC_MAP.put("心理", "安静思考的人");
    }

    private static final Map<IllustrationCharacterStyle, String> STYLE_VISUAL = new LinkedHashMap<>();
    static {
        STYLE_VISUAL.put(IllustrationCharacterStyle.HEALING,
                "柔和线条，温暖光晕，暖米黄+焦糖橙+奶油白+抹茶绿配色");
        STYLE_VISUAL.put(IllustrationCharacterStyle.CUTE,
                "圆润几何，柔和色块，奶油白+珊瑚粉+薄荷绿配色");
        STYLE_VISUAL.put(IllustrationCharacterStyle.DOODLE,
                "马克笔笔触，粗黑描边，米白纸纹+深棕+薄荷绿配色");
        STYLE_VISUAL.put(IllustrationCharacterStyle.WATERCOLOR,
                "毛笔线条，淡彩晕染，浅米纸纹+朱红+墨黑配色");
    }

    /**
     * 组装完整生图提示词。
     */
    public String build(String mainTitle, IllustrationCharacterStyle style) {
        String character = resolveCharacterDescription(mainTitle);
        String visual = STYLE_VISUAL.get(style);
        return "竖版卡片封面插画人物，" + character + "，" + visual +
                "，主体突出，背景干净留白，预留标题区（下方 30% 区域留白放标题），" +
                "无文字，竖版 9:16，治愈系插画，高清细腻";
    }

    /**
     * 主题 → 人物形象映射。命中关键词返回对应描述，否则返回默认「笑脸人物」。
     */
    public String resolveCharacterDescription(String mainTitle) {
        if (mainTitle == null || mainTitle.isBlank()) {
            return "笑脸人物";
        }
        for (Map.Entry<String, String> e : TOPIC_MAP.entrySet()) {
            if (mainTitle.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return "笑脸人物";
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=IllustrationPromptBuilderTest`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/card/illustration/IllustrationPromptBuilder.java src/test/java/com/example/aipassagecreator/card/illustration/IllustrationPromptBuilderTest.java
git commit -m "feat(card): IllustrationPromptBuilder 提示词母版 — 子风格视觉 + 主题映射

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: StaticIllustrationLibrary 静态素材库 + 素材入库

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/card/illustration/StaticIllustrationLibrary.java`
- Create: `src/test/java/com/example/aipassagecreator/card/illustration/StaticIllustrationLibraryTest.java`
- Create: `src/main/resources/illustration/LICENSES.md`
- Download 4 子风格素材到 `src/main/resources/illustration/{style}/`

**Interfaces:**
- Consumes: `IllustrationCharacterStyle`
- Produces: `String getUrl(IllustrationCharacterStyle style)` — 返回该子风格的静态素材 classpath 资源 URL

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.card.illustration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaticIllustrationLibraryTest {

    private final StaticIllustrationLibrary library = new StaticIllustrationLibrary();

    @Test
    void getUrl_allStyles_haveResource() {
        for (IllustrationCharacterStyle style : IllustrationCharacterStyle.values()) {
            String url = library.getUrl(style);
            assertNotNull(url, style.getName() + " 应有素材");
            assertFalse(url.isBlank(), style.getName() + " 素材 URL 非空");
        }
    }

    @Test
    void getUrl_staticResourceExists() {
        String url = library.getUrl(IllustrationCharacterStyle.HEALING);
        assertTrue(url.startsWith("classpath:illustration/healing/"), url);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=StaticIllustrationLibraryTest`
Expected: FAIL（素材文件不存在或 library 未实现）

- [ ] **Step 3: 下载素材（unDraw SVG 转 PNG）**

从 unDraw（https://undraw.co/illustrations）选择人物素材，按子风格下载，用 unDraw 在线换色匹配子风格色板，导出 PNG 到：
- `src/main/resources/illustration/healing/`（2-3 张暖色治愈人物）
- `src/main/resources/illustration/cute/`（2-3 张 Q版可爱人物）
- `src/main/resources/illustration/doodle/`（2-3 张手绘涂鸦人物）
- `src/main/resources/illustration/watercolor/`（2-3 张国风水墨人物）

> 若 unDraw 人物风格不足以覆盖全部 4 子风格，可混合使用爱给网（aigei.com）CC0 素材。每张素材在 `LICENSES.md` 记录来源 + 授权协议。

**Step 3a: 写 LICENSES.md**

```markdown
# 插画静态素材授权记录

> 熔断层素材均来自免费可商用来源。每张素材记录来源 + 授权协议，禁止未授权商用。

| 文件 | 来源 | 授权 | 备注 |
|---|---|---|---|
| healing/xxx.png | unDraw | 免费可商用、免署名、禁止AI训练/批量分发 | 熔断渲染合规 |
| cute/xxx.png | unDraw | 同上 | |
| doodle/xxx.png | Open Peeps / unDraw | 见具体素材 | 手绘涂鸦风 |
| watercolor/xxx.png | 爱给网 | CC0 或 CC 署名 | 需记录具体协议 |
```

- [ ] **Step 4: 实现 StaticIllustrationLibrary**

```java
package com.example.aipassagecreator.card.illustration;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 静态插画素材库 — AI 生图失败时的熔断兜底。
 * <p>素材内嵌于 classpath:illustration/{style}/，授权记录见同目录 LICENSES.md。
 * 每个子风格预置 2-3 张，AI 失败时按风格取第一张。</p>
 */
@Component
public class StaticIllustrationLibrary {

    private static final Map<IllustrationCharacterStyle, String> RESOURCES = new EnumMap<>(IllustrationCharacterStyle.class);

    static {
        RESOURCES.put(IllustrationCharacterStyle.HEALING, "classpath:illustration/healing/healing-1.png");
        RESOURCES.put(IllustrationCharacterStyle.CUTE, "classpath:illustration/cute/cute-1.png");
        RESOURCES.put(IllustrationCharacterStyle.DOODLE, "classpath:illustration/doodle/doodle-1.png");
        RESOURCES.put(IllustrationCharacterStyle.WATERCOLOR, "classpath:illustration/watercolor/watercolor-1.png");
    }

    /**
     * 返回指定子风格的静态素材 classpath URL。
     */
    public String getUrl(IllustrationCharacterStyle style) {
        return RESOURCES.get(style);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=StaticIllustrationLibraryTest`
Expected: PASS（素材文件存在于 classpath）

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/card/illustration/StaticIllustrationLibrary.java src/test/java/com/example/aipassagecreator/card/illustration/StaticIllustrationLibraryTest.java src/main/resources/illustration/
git commit -m "feat(card): StaticIllustrationLibrary 静态素材库 — AI 熔断兜底 + 授权记录

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: IllustrationImageService 生图编排（AI 主力 → 熔断）+ 单元测试

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/card/illustration/IllustrationImageService.java`
- Create: `src/test/java/com/example/aipassagecreator/card/illustration/IllustrationImageServiceTest.java`

**Interfaces:**
- Consumes: `IllustrationCharacterStyle`, `IllustrationPromptBuilder`, `StaticIllustrationLibrary`, `AgnesImageService`（`searchImage(String) → String?`）
- Produces: `String generateCoverImage(String mainTitle, IllustrationCharacterStyle style)` — 返回可用图片 URL（AI 成功→远程图；AI 失败→静态素材 URL）

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.card.illustration;

import com.example.aipassagecreator.service.AgnesImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IllustrationImageServiceTest {

    private IllustrationImageService service;
    private AgnesImageService agnes;
    private StaticIllustrationLibrary staticLib;

    @BeforeEach
    void setUp() {
        agnes = org.mockito.Mockito.mock(AgnesImageService.class);
        staticLib = org.mockito.Mockito.mock(StaticIllustrationLibrary.class);
        service = new IllustrationImageService(
                agnes,
                new IllustrationPromptBuilder(),
                staticLib);
    }

    @Test
    void generateCoverImage_aiSuccess_returnsAiUrl() {
        org.mockito.Mockito.when(agnes.searchImage(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("https://cos.example.com/cover.png");
        String url = service.generateCoverImage("标题", IllustrationCharacterStyle.HEALING);
        assertEquals("https://cos.example.com/cover.png", url);
    }

    @Test
    void generateCoverImage_aiFail_fallsBackToStatic() {
        org.mockito.Mockito.when(agnes.searchImage(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null);
        org.mockito.Mockito.when(staticLib.getUrl(IllustrationCharacterStyle.HEALING))
                .thenReturn("classpath:illustration/healing/healing-1.png");
        String url = service.generateCoverImage("标题", IllustrationCharacterStyle.HEALING);
        assertEquals("classpath:illustration/healing/healing-1.png", url);
    }

    @Test
    void generateCoverImage_aiThrows_fallsBackToStatic() {
        org.mockito.Mockito.when(agnes.searchImage(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("AI 服务异常"));
        org.mockito.Mockito.when(staticLib.getUrl(IllustrationCharacterStyle.HEALING))
                .thenReturn("classpath:illustration/healing/healing-1.png");
        String url = service.generateCoverImage("标题", IllustrationCharacterStyle.HEALING);
        assertEquals("classpath:illustration/healing/healing-1.png", url);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=IllustrationImageServiceTest`
Expected: 编译失败（类不存在）

- [ ] **Step 3: 实现 IllustrationImageService**

```java
package com.example.aipassagecreator.card.illustration;

import com.example.aipassagecreator.service.AgnesImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 插画人物生图编排服务 — AI 主力 + 静态熔断。
 * <p>AI 生图成功返回远程 URL（后续由 CardImageResolver base64 内联）；
 * 失败/异常静默回退静态素材，不中断卡片生成（熔断行为）。</p>
 */
@Slf4j
@Service
public class IllustrationImageService {

    private final AgnesImageService agnesImageService;
    private final IllustrationPromptBuilder promptBuilder;
    private final StaticIllustrationLibrary staticLibrary;

    public IllustrationImageService(AgnesImageService agnesImageService,
                                    IllustrationPromptBuilder promptBuilder,
                                    StaticIllustrationLibrary staticLibrary) {
        this.agnesImageService = agnesImageService;
        this.promptBuilder = promptBuilder;
        this.staticLibrary = staticLibrary;
    }

    /**
     * 生成封面插画人物图 URL。AI 失败自动熔断静态素材。
     */
    public String generateCoverImage(String mainTitle, IllustrationCharacterStyle style) {
        String prompt = promptBuilder.build(mainTitle, style);
        try {
            String url = agnesImageService.searchImage(prompt);
            if (url != null && !url.isBlank()) {
                log.info("插画人物 AI 生图成功: style={}, mainTitle={}", style.getName(), mainTitle);
                return url;
            }
            log.warn("插画人物 AI 生图返回空，熔断静态素材: style={}", style.getName());
        } catch (Exception e) {
            log.error("插画人物 AI 生图异常，熔断静态素材: style={}, err={}",
                    style.getName(), e.getMessage());
        }
        return staticLibrary.getUrl(style);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=IllustrationImageServiceTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/card/illustration/IllustrationImageService.java src/test/java/com/example/aipassagecreator/card/illustration/IllustrationImageServiceTest.java
git commit -m "feat(card): IllustrationImageService 生图编排 — AI主力+静态熔断

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: CardStyle / CardTemplateEngine 扩展 illustration 分支

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/card/CardStyle.java`
- Modify: `src/main/java/com/example/aipassagecreator/card/CardTemplateEngine.java`
- Create: `src/main/resources/templates/cards/illustration.html`
- Create: `src/test/java/com/example/aipassagecreator/card/CardTemplateEngineTest.java`（若不存在）或扩展现有测试

**Interfaces:**
- Consumes: `CardStyle.ILLUSTRATION`
- Produces: `CardTemplateEngine.render(List<PagePlan> pages, String style)` 现有签名不变；`render(pages, "illustration")` 时模板注入 `coverImageUrl/characterStyle/characterIconUrl/pageType` 变量。**注**：插画 URL 由 Task 6 的 CardService 传入，本任务仅保证模板可渲染。

- [ ] **Step 1: 修改 CardStyle 枚举**

在 `CardStyle.java` 中增加常量：

```java
public enum CardStyle {
    WARM("warm"),
    MINIMAL("minimal"),
    FREE("free"),
    HANDWRITING("handwriting"),
    ILLUSTRATION("illustration");
    // ... 其余不变
}
```

- [ ] **Step 2: 修改 CardTemplateEngine**

```java
public List<String> render(List<PagePlan> pages, String style) {
    String template = "cards/" + resolveStyle(style).getName();
    return pages.stream().map(page -> {
        Context ctx = new Context();
        ctx.setVariable("title", page.getTitle());
        ctx.setVariable("content", page.getContentMd());
        ctx.setVariable("pageNo", page.getPageNo());
        if (resolveStyle(style) == CardStyle.ILLUSTRATION) {
            ctx.setVariable("pageType", page.getPageType() != null ? page.getPageType() : "CONTENT");
            // imageBase64 优先（离线渲染内联），其次 imageUrl（远程 URL）
            String img = page.getImageBase64() != null ? page.getImageBase64() : page.getImageUrl();
            ctx.setVariable("coverImageUrl", img != null ? img : "");
            ctx.setVariable("characterStyle", "healing");
            ctx.setVariable("characterIconUrl", img != null ? img : "");
        }
        return templateEngine.process(template, ctx);
    }).toList();
}
```

- [ ] **Step 3: 写 illustration.html 模板**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
<meta charset="UTF-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: "Noto Sans CJK SC", "PingFang SC", "Microsoft YaHei", sans-serif;
    width: 1080px; height: 1920px; overflow: hidden;
    background: #F5E6D3; padding: 60px;
  }
  .card-page { width: 960px; height: 1800px; background: #FFFFFF;
    border-radius: 32px; padding: 60px; position: relative;
    box-shadow: 0 8px 32px rgba(0,0,0,0.08); overflow: hidden; }

  /* 封面页：人物大图 + 标题叠加 */
  .cover { display: flex; flex-direction: column; align-items: center; justify-content: flex-start; }
  .cover-image { width: 100%; height: 1100px; object-fit: cover; border-radius: 24px; }
  .cover-title { margin-top: 40px; font-size: 56px; font-weight: bold;
    color: #2D1810; text-align: center; line-height: 1.4; }
  .cover-content { margin-top: 20px; font-size: 30px; color: #4A3428; line-height: 1.7; }

  /* 内容页：右上角人物角标 */
  .content { position: relative; }
  .content-icon { position: absolute; top: 0; right: 0; width: 120px; height: 120px;
    object-fit: cover; border-radius: 16px; opacity: 0.9; }
  .content-title { font-size: 44px; font-weight: bold; color: #2D1810;
    margin-bottom: 24px; padding-right: 140px; }
  .content-body { font-size: 32px; line-height: 1.8; color: #4A3428; }
  .page-num { position: absolute; bottom: 60px; right: 80px;
    font-size: 24px; color: #C4A882; }
</style>
</head>
<body>
<!-- 封面页 -->
<div th:if="${pageType == 'COVER'}" class="card-page cover">
  <img th:if="${coverImageUrl != ''}" th:src="${coverImageUrl}"
       alt="" class="cover-image" />
  <div th:if="${title}" class="cover-title" th:text="${title}">主标题</div>
  <div th:if="${content}" class="cover-content" th:text="${content}">副标题</div>
</div>
<!-- 内容页 -->
<div th:if="${pageType != 'COVER'}" class="card-page content">
  <img th:if="${characterIconUrl != ''}" th:src="${characterIconUrl}"
       alt="" class="content-icon" />
  <div th:if="${title}" class="content-title" th:text="${title}">章节标题</div>
  <div class="content-body" th:text="${content}">内容</div>
  <div class="page-num" th:text="${pageNo}">1</div>
</div>
</body>
</html>
```

> **注**：`PagePlan.imageBase64` 在 Task 6 中会被填充为插画图 base64。本任务先用空字符串保证模板可渲染，Task 6 完成注入后封面显示插画图。

- [ ] **Step 4: 写/扩展 CardTemplateEngine 测试**

```java
package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.PagePlan;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import static org.junit.jupiter.api.Assertions.*;

class CardTemplateEngineIllustrationTest {

    private CardTemplateEngine engine;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        engine = new CardTemplateEngine(templateEngine);
    }

    @Test
    void render_illustration_usesIllustrationTemplate() {
        PagePlan cover = new PagePlan();
        cover.setPageType("COVER");
        cover.setTitle("标题");
        cover.setContentMd("内容");
        cover.setImageBase64("data:image/png;base64,xxx");
        String html = engine.render(java.util.List.of(cover), "illustration").get(0);
        assertTrue(html.contains("cover-title"));
        assertTrue(html.contains("data:image/png;base64,xxx"));
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=CardTemplateEngineIllustrationTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/card/CardStyle.java src/main/java/com/example/aipassagecreator/card/CardTemplateEngine.java src/main/resources/templates/cards/illustration.html src/test/java/com/example/aipassagecreator/card/CardTemplateEngineIllustrationTest.java
git commit -m "feat(card): CardStyle+CardTemplateEngine 扩展 illustration — 封面大图+内页角标模板

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: CardService / CardController / CardAsyncService 透传 characterStyle

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/card/model/CardGenerateRequest.java`
- Modify: `src/main/java/com/example/aipassagecreator/card/CardController.java`
- Modify: `src/main/java/com/example/aipassagecreator/card/CardAsyncService.java`
- Modify: `src/main/java/com/example/aipassagecreator/card/CardService.java`
- Create: `src/test/java/com/example/aipassagecreator/card/CardServiceIllustrationTest.java`

**Interfaces:**
- Consumes: `IllustrationImageService.generateCoverImage(String, IllustrationCharacterStyle)`, `CardStyle.ILLUSTRATION`
- Produces: `CardService.generate(..., cardStyle, characterStyle, taskId, methodologyName)` — style=illustration 时封面页 imageBase64 填入插画图

- [ ] **Step 1: 修改 CardGenerateRequest 加字段**

```java
@Data
public class CardGenerateRequest implements Serializable {
    // ... 现有字段不变
    /** 插画子风格（healing/cute/doodle/watercolor），仅 cardStyle=illustration 生效 */
    private String characterStyle;
}
```

- [ ] **Step 2: 修改 CardController 透传**

在 `preview` / `generate` 方法中解析 characterStyle 并透传：

```java
// generate 方法中，resolveCardStyle 之后：
String characterStyle = request.getCharacterStyle() != null
        ? IllustrationCharacterStyle.from(request.getCharacterStyle()).getName()
        : IllustrationCharacterStyle.HEALING.getName();
// 传给 cardAsyncService.generateCards(taskId, cardStyle, characterStyle, methodologyName, loginUserId)
```

**Step 3: 修改 CardAsyncService 透传**

```java
public void generateCards(String taskId, String cardStyle, String characterStyle,
                          String methodologyName, Long loginUserId) {
    // ... 现有逻辑
    cardService.generate(fullContent, article.getMainTitle(),
            article.getSubTitle(), article.getCoverImage(),
            cardStyle, characterStyle, taskId, methodologyName);
}
```

- [ ] **Step 4: 修改 CardService 注入插画 URL**

在 `generate()` 方法中，style=illustration 时给封面页注入插画 URL：

```java
// 分页后，渲染前：
List<PagePlan> pages = planner.plan(fullContent, mainTitle, subTitle, coverImage);
if (CardStyle.from(cardStyle) == CardStyle.ILLUSTRATION) {
    IllustrationCharacterStyle charStyle = IllustrationCharacterStyle.from(characterStyle);
    String illusUrl = illustrationImageService.generateCoverImage(mainTitle, charStyle);
    // 远程 COS 插画图 → 经 CardImageResolver 转 base64 内联（离线渲染）
    String illusDataUrl = cardImageResolver.toDataUrl(illusUrl);
    // 将插画 base64 设置为封面页 imageBase64（内容页角标复用同一图）
    pages.stream().filter(p -> "COVER".equals(p.getPageType())).findFirst()
            .ifPresent(p -> p.setImageBase64(illusDataUrl));
}
// 渲染
List<String> htmls = templateEngine.render(pages, cardStyle);
```

> **注**：封面插画为远程 URL 时，需用现有 `CardImageResolver.toDataUrl(url)` 转 base64 再注入模板（走离线渲染）。实现时按 `CardImageResolver` 实际签名调用。`generate()` 构造器注入 `IllustrationImageService` 和 `CardImageResolver`。

- [ ] **Step 5: 写 CardService 集成测试**

```java
package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.illustration.IllustrationImageService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardServiceIllustrationTest {

    @Test
    void generate_illustrationStyle_setsCoverImage() {
        // mock IllustrationImageService 返回静态素材 URL
        // 调用 cardService.generate(..., "illustration", "healing", ...)
        // 断言封面页 imageBase64 非空
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=CardServiceIllustrationTest,CardServiceIntegrationTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/card/model/CardGenerateRequest.java src/main/java/com/example/aipassagecreator/card/CardController.java src/main/java/com/example/aipassagecreator/card/CardAsyncService.java src/main/java/com/example/aipassagecreator/card/CardService.java src/test/java/com/example/aipassagecreator/card/CardServiceIllustrationTest.java
git commit -m "feat(card): CardService 透传 characterStyle — illustration 封面注入插画图

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: 全量测试 + 安全回归 + 收尾

**Files:**
- Verify: 全部卡片测试
- 必要时扩展 `CardControllerSecurityTest`

- [ ] **Step 1: 运行全量后端测试**

Run: `mvn test -Dspring.profiles.active=test`
Expected: 全部通过（含既有 118+ 测试 + 新增插画测试）

- [ ] **Step 2: 前端 type-check + build**

Run: `cd frontend && npm run type-check && npm run build`
Expected: 零错误（若无前端改动则跳过）

- [ ] **Step 3: 检查 git status 只含预期文件**

Run: `git status`
Expected: 仅新增/修改的插画相关文件

- [ ] **Step 4: 全量提交**

```bash
git add -A
git commit -m "test(card): 插画人物卡片风格全量回归

Co-Authored-By: Claude <noreply@anthropic.com>"
```

- [ ] **Step 5: 推送（可选）**

```bash
bash scripts/git-push.sh github dev
```

---

## Self-Review

**1. Spec coverage:**
- CardStyle + ILLUSTRATION → Task 5 ✅
- IllustrationImageService 编排 → Task 4 ✅
- IllustrationPromptBuilder 母版 → Task 2 ✅
- IllustrationCharacterStyle 枚举 → Task 1 ✅
- StaticIllustrationLibrary + 素材入库 + LICENSES → Task 3 ✅
- illustration.html 模板 → Task 5 ✅
- CardController/CardService/CardAsyncService 透传 → Task 6 ✅
- CardGenerateRequest + characterStyle → Task 6 ✅
- 配额接入 → 复用现有 quotaService（Task 6 未改动，符合设计）
- 前端双入口 → **out-of-scope**（设计文档 2.2，后续阶段）
- 测试 + 安全回归 → Task 7 ✅

**2. Placeholder scan:** 无 TBD/TODO。Task 5/6 中"实际实施时以 PagePlan 现有字段为准"、"按 CardImageResolver 实际签名调用"为实施提示，非占位。

**3. Type consistency:** `generateCoverImage(String, IllustrationCharacterStyle) → String` 在 Task 4/6 一致；`StaticIllustrationLibrary.getUrl(IllustrationCharacterStyle) → String` 一致；`IllustrationCharacterStyle.from(String)` 一致。
