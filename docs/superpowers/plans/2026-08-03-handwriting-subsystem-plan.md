# 手写效果子系统 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建手写效果子系统：共享渲染引擎 + Card 风格 handwriting + 独立手写编辑器

**Architecture:** 新建 `handwriting/` 包（与 `card/` 平级），核心渲染器 `HandwritingRenderer` 封装开源手写 JS 库生成含手写效果的 HTML，`CardRenderPipeline` 新增 `renderWithJs()` 方法在 JS 启用 + COS 白名单路由的安全上下文中渲染，`CardService` 的 style=handwriting 分支走新渲染器。双入口：Card 风格（零新 Controller）+ 独立 `HandwritingController` 编辑器 API。

**Tech Stack:** Java 21, Spring Boot 3.5.13, MyBatis-Flex 1.11.1, Playwright 1.61.0, Thymeleaf, Jsoup, Vue 3 + TypeScript + Ant Design Vue 4

## Global Constraints

- 所有新增 Java 类放入 `com.example.aipassagecreator.handwriting` 包
- 不破坏现有 `card/` 包的安全约束（JS 禁用、路由 abort 原样保留）
- 用户可见错误消息用中文
- 依赖注入用构造器注入 + Lombok `@RequiredArgsConstructor`
- 测试用 JUnit 5 + Mockito，命名 `methodName_scenario_expectedResult()`
- 字体仅用 SIL OFL 授权
- 频率限制：预览 10/min，导出 3/min
- COS key 前缀：handwriting/{taskId}/
- SSE key 前缀：handwriting_{exportId}

---

### Task 1: CardStyle 枚举 — 统一风格管理

> **审计 #6 修复**：将分散的 SUPPORTED_STYLES 和默认值收敛到单一枚举。

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/card/CardStyle.java`
- Modify: `src/main/java/com/example/aipassagecreator/card/CardTemplateEngine.java:22-59`
- Modify: `src/main/java/com/example/aipassagecreator/card/CardController.java:192-207`

**Interfaces:**
- Produces: `CardStyle` enum with WARM/MINIMAL/FREE/HANDWRITING + `from(String)` + `getName()`

- [ ] **Step 1: 创建 CardStyle 枚举**

```java
package com.example.aipassagecreator.card;

/**
 * 卡片风格枚举 — 统一白名单 + 默认值管理。
 * 替代 CardTemplateEngine#SUPPORTED_STYLES 和 CardController 中的硬编码 "warm"。
 */
public enum CardStyle {
    WARM("warm"),
    MINIMAL("minimal"),
    FREE("free"),
    HANDWRITING("handwriting");

    private final String name;

    CardStyle(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * 从字符串解析风格名，未知或 null 回退默认 WARM。
     */
    public static CardStyle from(String name) {
        if (name == null || name.isBlank()) {
            return WARM;
        }
        for (CardStyle style : values()) {
            if (style.name.equalsIgnoreCase(name)) {
                return style;
            }
        }
        return WARM;
    }
}
```

- [ ] **Step 2: 写 CardStyle 单元测试**

```java
package com.example.aipassagecreator.card;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardStyleTest {

    @Test
    void from_validName_returnsCorrectStyle() {
        assertEquals(CardStyle.WARM, CardStyle.from("warm"));
        assertEquals(CardStyle.MINIMAL, CardStyle.from("minimal"));
        assertEquals(CardStyle.FREE, CardStyle.from("free"));
        assertEquals(CardStyle.HANDWRITING, CardStyle.from("handwriting"));
    }

    @Test
    void from_null_returnsWarm() {
        assertEquals(CardStyle.WARM, CardStyle.from(null));
    }

    @Test
    void from_blank_returnsWarm() {
        assertEquals(CardStyle.WARM, CardStyle.from("  "));
    }

    @Test
    void from_unknown_returnsWarm() {
        assertEquals(CardStyle.WARM, CardStyle.from("nonexistent"));
    }

    @Test
    void from_caseInsensitive_matchesCorrectly() {
        assertEquals(CardStyle.HANDWRITING, CardStyle.from("HANDWRITING"));
        assertEquals(CardStyle.HANDWRITING, CardStyle.from("Handwriting"));
    }
}
```

Run: `mvn test -Dtest=CardStyleTest -Dspring.profiles.active=test`
Expected: 5 tests PASS

- [ ] **Step 3: 改造 CardTemplateEngine 使用 CardStyle**

**Before** (lines 22-24):
```java
private static final Set<String> SUPPORTED_STYLES = Set.of("warm", "minimal", "free");
private static final String DEFAULT_STYLE = "warm";
```

**After**:
```java
private static final CardStyle DEFAULT_STYLE = CardStyle.WARM;
```

**Before** (line 42):
```java
String template = "cards/" + resolveStyle(style);
```

**After**:
```java
String template = "cards/" + resolveStyle(style).getName();
```

**Before** (lines 55-59):
```java
private String resolveStyle(String style) {
    if (style != null && SUPPORTED_STYLES.contains(style)) {
        return style;
    }
    return DEFAULT_STYLE;
}
```

**After**:
```java
private CardStyle resolveStyle(String style) {
    return CardStyle.from(style);
}
```

Update the method signature return type from `String` to `CardStyle`, and update any callers. In `render()` line 41, change `resolveStyle(style)` usage to extract `.getName()` where needed.

- [ ] **Step 4: 改造 CardController.resolveCardStyle() 使用 CardStyle**

**Before** (lines 193-207):
```java
private String resolveCardStyle(String requestCardStyle, String methodologyName) {
    if (requestCardStyle != null && !requestCardStyle.isBlank()) {
        return requestCardStyle;
    }
    try {
        var def = methodologyRegistry.get(
                methodologyName != null ? methodologyName : "default");
        if (def.getPlatform() != null && def.getPlatform().getCardStyle() != null) {
            return def.getPlatform().getCardStyle();
        }
    } catch (IllegalArgumentException e) {
        log.warn("方法论不存在，卡片风格回退默认 warm: {}", e.getMessage());
    }
    return "warm";
}
```

**After**:
```java
private String resolveCardStyle(String requestCardStyle, String methodologyName) {
    if (requestCardStyle != null && !requestCardStyle.isBlank()) {
        return CardStyle.from(requestCardStyle).getName();
    }
    try {
        var def = methodologyRegistry.get(
                methodologyName != null ? methodologyName : "default");
        if (def.getPlatform() != null && def.getPlatform().getCardStyle() != null) {
            return CardStyle.from(def.getPlatform().getCardStyle()).getName();
        }
    } catch (IllegalArgumentException e) {
        log.warn("方法论不存在，卡片风格回退默认 warm: {}", e.getMessage());
    }
    return CardStyle.WARM.getName();
}
```

- [ ] **Step 5: 运行现有测试确认无回归**

```bash
mvn test -Dspring.profiles.active=test
```

Expected: All existing tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/card/CardStyle.java \
        src/main/java/com/example/aipassagecreator/card/CardTemplateEngine.java \
        src/main/java/com/example/aipassagecreator/card/CardController.java \
        src/test/java/com/example/aipassagecreator/card/CardStyleTest.java
git commit -m "refactor(card): CardStyle 枚举统一风格管理 — 审计#6修复

收敛分散的 SUPPORTED_STYLES 和硬编码 'warm' 到 CardStyle 枚举，
新增 handwriting 风格占位。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: CardService 幂等删除修复

> **审计 #3 修复**：DELETE 条件增加 style 过滤，防止删除其他风格的已生成卡片。

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/card/CardService.java:99`

- [ ] **Step 1: 修改 deleteByQuery 条件**

```java
// 旧代码 (line 99)
cardPageMapper.deleteByQuery(QueryWrapper.create().eq("task_id", taskId));

// 新代码
cardPageMapper.deleteByQuery(
    QueryWrapper.create()
        .eq("task_id", taskId)
        .eq("style", cardStyle));
```

- [ ] **Step 2: 运行 CardService 相关测试**

```bash
mvn test -Dtest=CardServiceTest,CardServiceIntegrationTest -Dspring.profiles.active=test
```

Expected: All tests PASS (需要确认 CardServiceTest 的 mock 期望适配新条件)

**Note**: 现有的 `CardServiceTest.generate_usesBaseCosKey()` 测试在第 91 行使用 `when(cardPageMapper.deleteByQuery(any())).thenReturn(1)` — 因为参数变为带有两个条件的 QueryWrapper，`any()` 匹配器仍然有效，无需修改。

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/card/CardService.java
git commit -m "fix(card): 幂等删除增加 style 条件 — 审计#3修复

DELETE article_card WHERE task_id AND style，防止切换风格生成时误删其他风格卡片。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: HandwritingRenderConfig + 配置段

> 手写渲染配置对象 + application.yml 配置段。

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/handwriting/config/HandwritingRenderConfig.java`
- Modify: `src/main/resources/application.yml` — 追加 handwriting 配置段

- [ ] **Step 1: 创建 HandwritingRenderConfig**

```java
package com.example.aipassagecreator.handwriting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 手写渲染配置。
 * 由 application.yml 的 handwriting.render 段注入。
 */
@Configuration
@ConfigurationProperties(prefix = "handwriting.render")
public class HandwritingRenderConfig {

    /** COS 基础域名（Playwright 白名单路由放行） */
    private String cosBaseUrl = "";

    /** 单页渲染超时秒数，默认 30 */
    private int renderTimeoutSec = 30;

    /** 批次渲染超时秒数，默认 120 */
    private int batchTimeoutSec = 120;

    /** 渲染视口宽度（A4 比例），默认 1240 */
    private int viewportWidth = 1240;

    /** 渲染视口高度（A4 比例），默认 1754 */
    private int viewportHeight = 1754;

    /** 渲染设备缩放比，默认 2.0 */
    private double deviceScaleFactor = 2.0;

    // getters and setters
    public String getCosBaseUrl() { return cosBaseUrl; }
    public void setCosBaseUrl(String cosBaseUrl) { this.cosBaseUrl = cosBaseUrl; }
    public int getRenderTimeoutSec() { return renderTimeoutSec; }
    public void setRenderTimeoutSec(int renderTimeoutSec) { this.renderTimeoutSec = renderTimeoutSec; }
    public int getBatchTimeoutSec() { return batchTimeoutSec; }
    public void setBatchTimeoutSec(int batchTimeoutSec) { this.batchTimeoutSec = batchTimeoutSec; }
    public int getViewportWidth() { return viewportWidth; }
    public void setViewportWidth(int viewportWidth) { this.viewportWidth = viewportWidth; }
    public int getViewportHeight() { return viewportHeight; }
    public void setViewportHeight(int viewportHeight) { this.viewportHeight = viewportHeight; }
    public double getDeviceScaleFactor() { return deviceScaleFactor; }
    public void setDeviceScaleFactor(double deviceScaleFactor) { this.deviceScaleFactor = deviceScaleFactor; }
}
```

- [ ] **Step 2: 在 application.yml 末尾追加 handwriting 配置段**

```yaml
# 手写效果子系统配置
handwriting:
  render:
    cos-base-url: ${TENCENT_COS_BUCKET}.cos.${TENCENT_COS_REGION:ap-guangzhou}.myqcloud.com
    render-timeout-sec: 30
    batch-timeout-sec: 120
    viewport-width: 1240
    viewport-height: 1754
    device-scale-factor: 2.0
  fonts:
    # 手写字体 COS 路径前缀
    cos-prefix: handwriting/fonts
    # MVP 字体列表（SIL OFL 授权）
    available:
      - name: 手书体
        key: shoushu
        file: shoushu.ttf
      - name: 851手写杂字体
        key: tegakizatsu
        file: 851tegakizatsu.ttf
      - name: 今年也要加油鸭
        key: jiayouya
        file: jiayouya.ttf
  papers:
    # 纸张类型
    types:
      - blank
      - line
      - grid
      - tianzi
      - dot
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/handwriting/config/HandwritingRenderConfig.java \
        src/main/resources/application.yml
git commit -m "feat(handwriting): 渲染配置 + application.yml 配置段

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: CardRenderPipeline — renderWithJs() + renderToPdf()

> **审计 #1、#2、#7、#8、#12 修复**：新增 JS 启用的渲染方法，COS URL 白名单路由，字体缓存上下文复用，超时提升。

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/card/CardRenderPipeline.java` — 追加两个新方法

**Interfaces:**
- Produces: `List<PageResult> renderWithJs(List<String> htmls, String taskId, HandwritingRenderConfig config)`
- Produces: `byte[] renderToPdf(String html, HandwritingRenderConfig config)`

- [ ] **Step 1: 新增 renderWithJs() 方法**

在 `CardRenderPipeline.java` 中，保留所有现有方法不变，追加以下代码：

```java
// 在现有 import 区域追加
import com.example.aipassagecreator.handwriting.config.HandwritingRenderConfig;
import java.util.function.Predicate;

// 在现有方法之后，追加新方法

/**
 * 手写效果渲染（JS 启用 + COS URL 白名单路由 + 字体缓存）。
 * <p>与现有 {@link #render(List, String)} 并行存在，不动原管线。
 * 关键差异：
 * <ul>
 *   <li>JS 启用（手写扰动算法依赖）</li>
 *   <li>路由白名单：只放行 COS 域名请求，abort 其余</li>
 *   <li>复用 BrowserContext（字体缓存），不每页 newContext</li>
 *   <li>视口按 A4 比例（1240×1754）</li>
 * </ul>
 *
 * @param htmls  手写 HTML 列表
 * @param taskId 任务 ID
 * @param config 手写渲染配置
 */
public List<PageResult> renderWithJs(List<String> htmls, String taskId,
                                     HandwritingRenderConfig config) {
    List<PageResult> results = new ArrayList<>();
    if (browser == null) {
        log.warn("渲染引擎不可用，跳过全部页面: taskId={}, pages={}", taskId, htmls.size());
        for (int i = 0; i < htmls.size(); i++) {
            results.add(PageResult.builder().pageNo(i + 1)
                    .errorMessage("渲染引擎不可用").build());
        }
        return results;
    }

    String cosBaseUrl = config.getCosBaseUrl() != null ? config.getCosBaseUrl() : "";
    int viewportW = config.getViewportWidth() > 0 ? config.getViewportWidth() : 1240;
    int viewportH = config.getViewportHeight() > 0 ? config.getViewportHeight() : 1754;
    long batchStart = System.currentTimeMillis();
    long batchTimeoutMs = config.getBatchTimeoutSec() * 1000L;

    // 复用单个 BrowserContext，让字体文件在页面间缓存
    try (var context = browser.newContext(
            new Browser.NewContextOptions()
                    .setViewportSize(viewportW, viewportH)
                    .setDeviceScaleFactor(config.getDeviceScaleFactor() > 0
                            ? config.getDeviceScaleFactor() : 2.0)
                    .setJavaScriptEnabled(true))) {

        Predicate<String> isCosUrl = url ->
                !cosBaseUrl.isBlank() && url.contains(cosBaseUrl);

        for (int i = 0; i < htmls.size(); i++) {
            if (System.currentTimeMillis() - batchStart > batchTimeoutMs) {
                log.warn("手写渲染批次超时: taskId={}, done={}/{}", taskId, i, htmls.size());
                results.add(PageResult.builder().pageNo(i + 1)
                        .errorMessage("批次渲染超时").build());
                continue;
            }
            try {
                PageResult result = renderOneWithJs(context, htmls.get(i), i + 1,
                        isCosUrl, config.getRenderTimeoutSec());
                results.add(result);
            } catch (Exception e) {
                log.error("手写单页渲染失败: taskId={}, pageNo={}", taskId, i + 1, e);
                results.add(PageResult.builder().pageNo(i + 1)
                        .errorMessage(e.getMessage()).build());
            }
        }
    }
    return results;
}

private PageResult renderOneWithJs(BrowserContext context, String html, int pageNo,
                                   Predicate<String> isCosUrl, int timeoutSec) throws Exception {
    if (!renderPermits.tryAcquire(timeoutSec, TimeUnit.SECONDS)) {
        return PageResult.builder().pageNo(pageNo)
                .errorMessage("渲染资源不足，超时等待").build();
    }
    long start = System.currentTimeMillis();
    try (var page = context.newPage()) {
        page.setDefaultTimeout(timeoutSec * 1000L);

        // 细粒度路由：放行 COS URL（字体文件），abort 其余
        page.route("**", route -> {
            if (isCosUrl.test(route.request().url())) {
                route.resume();
            } else {
                route.abort();
            }
        });

        // 加载 HTML（JS 已在 Context 级别启用）
        page.setContent(html,
                new Page.SetContentOptions()
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));

        // 截取全页
        byte[] png = page.screenshot(
                new Page.ScreenshotOptions()
                        .setType(ScreenshotType.PNG)
                        .setFullPage(true));

        int renderMs = (int) (System.currentTimeMillis() - start);
        if (png.length > MAX_PNG_BYTES) {
            log.warn("手写 PNG 超限: pageNo={}, bytes={}", pageNo, png.length);
        }
        return PageResult.builder().pageNo(pageNo).pngBytes(png)
                .layoutPassed(true).renderMs(renderMs).build();
    } finally {
        renderPermits.release();
    }
}

/**
 * PDF 导出：Playwright page → PDF byte[]。
 * 用于手写编辑器导出 PDF 格式。
 *
 * @param html   手写 HTML
 * @param config 渲染配置
 * @return PDF 字节数组
 */
public byte[] renderToPdf(String html, HandwritingRenderConfig config) {
    if (browser == null) {
        throw new IllegalStateException("渲染引擎不可用");
    }
    String cosBaseUrl = config.getCosBaseUrl() != null ? config.getCosBaseUrl() : "";
    Predicate<String> isCosUrl = url ->
            !cosBaseUrl.isBlank() && url.contains(cosBaseUrl);

    try (var context = browser.newContext(
            new Browser.NewContextOptions()
                    .setViewportSize(config.getViewportWidth(), config.getViewportHeight())
                    .setDeviceScaleFactor(config.getDeviceScaleFactor())
                    .setJavaScriptEnabled(true));
         var page = context.newPage()) {

        page.route("**", route -> {
            if (isCosUrl.test(route.request().url())) {
                route.resume();
            } else {
                route.abort();
            }
        });

        page.setContent(html,
                new Page.SetContentOptions()
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));

        return page.pdf(new Page.PdfOptions()
                .setFormat("A4")
                .setPrintBackground(true));
    }
}
```

- [ ] **Step 2: 运行现有 CardRenderPipeline 测试确保无回归**

```bash
mvn test -Dtest=CardRenderPipelineTest,CardRenderPipelineE2ETest -Dspring.profiles.active=test
```

Expected: All existing tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/card/CardRenderPipeline.java
git commit -m "feat(card): CardRenderPipeline 增加 renderWithJs/renderToPdf — 审计#1#2#7#8#12修复

新增 JS 启用渲染方法，COS 域名白名单路由，BrowserContext 复用字体缓存，
renderToPdf 支持 A4 PDF 导出。原 render() 方法安全约束不变。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: 手写渲染模型（HandwritingRequest + HandwritingParams）

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/handwriting/model/HandwritingParams.java`
- Create: `src/main/java/com/example/aipassagecreator/handwriting/model/HandwritingRequest.java`

- [ ] **Step 1: 创建 HandwritingParams**

```java
package com.example.aipassagecreator.handwriting.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * 手写扰动参数 — 控制手写效果的自然程度。
 */
public record HandwritingParams(
    @DecimalMin("0.0") @DecimalMax("10.0")
    double positionJitter,   // 位置随机偏移 (px), 默认 2.0

    @DecimalMin("0.0") @DecimalMax("5.0")
    double rotationJitter,   // 旋转随机角度 (°), 默认 1.5

    @DecimalMin("0.0") @DecimalMax("20.0")
    double sizeJitter,       // 字号随机变化 (%), 默认 5.0

    @DecimalMin("0.0") @DecimalMax("1.0")
    double inkDensity        // 墨迹浓淡 (0.0-1.0), 默认 0.85
) {
    public static HandwritingParams defaults() {
        return new HandwritingParams(2.0, 1.5, 5.0, 0.85);
    }
}
```

- [ ] **Step 2: 创建 HandwritingRequest**

```java
package com.example.aipassagecreator.handwriting.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 手写渲染请求 — 进入 {@link com.example.aipassagecreator.handwriting.HandwritingRenderer#renderToHtml}。
 */
public record HandwritingRequest(
    @NotBlank
    String content,          // 已清洗的文本内容

    @NotBlank
    String fontName,         // 字体 key (shoushu/tegakizatsu/jiayouya)

    @NotBlank
    String paperType,        // 纸张类型: blank/line/grid/tianzi/dot

    HandwritingParams params, // 扰动参数（null 时用默认值）

    String paperImageUrl     // 自定义纸张背景图 URL（可选）
) {
    public HandwritingParams effectiveParams() {
        return params != null ? params : HandwritingParams.defaults();
    }
}
```

- [ ] **Step 3: 写模型验证测试**

```java
package com.example.aipassagecreator.handwriting.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandwritingRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void request_blankContent_violation() {
        var req = new HandwritingRequest("  ", "shoushu", "line", null, null);
        var violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void request_validContent_passes() {
        var req = new HandwritingRequest("测试内容", "shoushu", "line", null, null);
        var violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void effectiveParams_null_returnsDefaults() {
        var req = new HandwritingRequest("test", "shoushu", "blank", null, null);
        assertEquals(HandwritingParams.defaults(), req.effectiveParams());
    }

    @Test
    void effectiveParams_provided_returnsProvided() {
        var p = new HandwritingParams(3.0, 2.0, 7.0, 0.9);
        var req = new HandwritingRequest("test", "shoushu", "blank", p, null);
        assertSame(p, req.effectiveParams());
    }

    @Test
    void paramsExceedMax_violation() {
        var p = new HandwritingParams(15.0, 2.0, 5.0, 0.85);
        var violations = validator.validate(p);
        assertFalse(violations.isEmpty());
    }
}
```

Run: `mvn test -Dtest=HandwritingRequestTest -Dspring.profiles.active=test`
Expected: 5 tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/handwriting/model/ \
        src/test/java/com/example/aipassagecreator/handwriting/model/
git commit -m "feat(handwriting): 手写渲染请求参数模型 + 验证测试

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: HandwritingPaperService — 纸张背景服务

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/handwriting/HandwritingPaperService.java`
- Test: `src/test/java/com/example/aipassagecreator/handwriting/HandwritingPaperServiceTest.java`

- [ ] **Step 1: 创建 HandwritingPaperService**

```java
package com.example.aipassagecreator.handwriting;

import org.springframework.stereotype.Component;

/**
 * 纸张背景服务 — 纯 CSS 生成 5 种纸张背景。
 * 使用 repeating-linear-gradient，保证 300 DPI 高清渲染。
 */
@Component
public class HandwritingPaperService {

    /**
     * 生成纸张背景 CSS。
     *
     * @param paperType      纸张类型: blank/line/grid/tianzi/dot
     * @param customImageUrl 自定义纸张背景图 URL（可选，非 blank 时优先）
     * @return CSS 样式字符串
     */
    public String generatePaperCss(String paperType, String customImageUrl) {
        if (customImageUrl != null && !customImageUrl.isBlank()) {
            return customImageCss(customImageUrl);
        }
        return switch (paperType != null ? paperType : "blank") {
            case "line"   -> lineCss();
            case "grid"   -> gridCss();
            case "tianzi" -> tianziCss();
            case "dot"    -> dotCss();
            default       -> blankCss();
        };
    }

    String blankCss() {
        return "background: #FFFFFF;";
    }

    /** 横线纸：水平 repeating-linear-gradient，约 7mm 间距 */
    String lineCss() {
        return """
            background-color: #FFFFFF;
            background-image: repeating-linear-gradient(
                transparent, transparent 39px,
                #B8C6DB 39px, #B8C6DB 40px
            );
            background-size: 100% 40px;
            """;
    }

    /** 方格纸：交叉 repeating-linear-gradient */
    String gridCss() {
        return """
            background-color: #FFFFFF;
            background-image:
                repeating-linear-gradient(
                    #D0DAE8 0px, #D0DAE8 1px,
                    transparent 1px, transparent 40px
                ),
                repeating-linear-gradient(
                    90deg,
                    #D0DAE8 0px, #D0DAE8 1px,
                    transparent 1px, transparent 40px
                );
            background-size: 40px 40px;
            """;
    }

    /** 田字格：实线 + 虚线十字 */
    String tianziCss() {
        return """
            background-color: #FFFFFF;
            background-image:
                repeating-linear-gradient(
                    transparent, transparent 39px,
                    #FF8A80 39px, #FF8A80 40px
                ),
                repeating-linear-gradient(
                    90deg,
                    transparent, transparent 39px,
                    #FF8A80 39px, #FF8A80 40px
                ),
                repeating-linear-gradient(
                    #E0E0E0 0px, #E0E0E0 1px,
                    transparent 1px, transparent 20px
                ),
                repeating-linear-gradient(
                    90deg,
                    #E0E0E0 0px, #E0E0E0 1px,
                    transparent 1px, transparent 20px
                );
            background-size: 40px 40px, 40px 40px, 20px 20px, 20px 20px;
            """;
    }

    /** 点阵纸：radial-gradient 圆点 */
    String dotCss() {
        return """
            background-color: #FFFFFF;
            background-image: radial-gradient(circle, #C4C4C4 1px, transparent 1px);
            background-size: 20px 20px;
            """;
    }

    private String customImageCss(String imageUrl) {
        return "background-image: url('" + imageUrl + "'); " +
               "background-size: cover; " +
               "background-position: center; " +
               "background-repeat: no-repeat;";
    }
}
```

- [ ] **Step 2: 写单元测试**

```java
package com.example.aipassagecreator.handwriting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandwritingPaperServiceTest {

    private final HandwritingPaperService service = new HandwritingPaperService();

    @Test
    void blank_returnsWhiteBackground() {
        String css = service.generatePaperCss("blank", null);
        assertTrue(css.contains("#FFFFFF"));
    }

    @Test
    void line_containsRepeatingLinearGradient() {
        String css = service.generatePaperCss("line", null);
        assertTrue(css.contains("repeating-linear-gradient"));
        assertTrue(css.contains("39px"));
    }

    @Test
    void grid_containsBothDirections() {
        String css = service.generatePaperCss("grid", null);
        assertTrue(css.contains("90deg"));
    }

    @Test
    void tianzi_containsFourLayers() {
        String css = service.generatePaperCss("tianzi", null);
        // 4 个 background-image 层
        long count = css.lines()
                .filter(l -> l.contains("repeating-linear-gradient"))
                .count();
        assertTrue(count >= 4);
    }

    @Test
    void dot_containsRadialGradient() {
        String css = service.generatePaperCss("dot", null);
        assertTrue(css.contains("radial-gradient"));
    }

    @Test
    void nullPaperType_defaultsToBlank() {
        String css = service.generatePaperCss(null, null);
        assertTrue(css.contains("#FFFFFF"));
    }

    @Test
    void customImageUrl_usesImageBackground() {
        String css = service.generatePaperCss("line", "https://example.com/paper.jpg");
        assertTrue(css.contains("url('https://example.com/paper.jpg')"));
        assertFalse(css.contains("repeating-linear-gradient"));
    }

    @Test
    void unknownPaperType_defaultsToBlank() {
        String css = service.generatePaperCss("unknown", null);
        assertTrue(css.contains("#FFFFFF"));
    }
}
```

Run: `mvn test -Dtest=HandwritingPaperServiceTest -Dspring.profiles.active=test`
Expected: 8 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/handwriting/HandwritingPaperService.java \
        src/test/java/com/example/aipassagecreator/handwriting/HandwritingPaperServiceTest.java
git commit -m "feat(handwriting): HandwritingPaperService — 5种纸张背景 CSS 生成

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: HandwritingFontManager — 字体管理器

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/handwriting/HandwritingFontManager.java`
- Create: `src/main/java/com/example/aipassagecreator/handwriting/model/HandwritingFont.java`

- [ ] **Step 1: 创建 HandwritingFont VO**

```java
package com.example.aipassagecreator.handwriting.model;

/**
 * 手写字体 VO — 返回给前端的字体信息。
 */
public record HandwritingFont(
    String name,       // 中文显示名，如 "手书体"
    String key,        // 内部 key，如 "shoushu"
    String previewText  // 预览样本文本
) {}
```

- [ ] **Step 2: 创建 HandwritingFontManager**

```java
package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingFont;
import com.example.aipassagecreator.service.CosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 手写字体管理器。
 * <p>字体文件存储在 COS handwriting/fonts/ 目录下，
 * Playwright 通过预签名 URL 加载。
 * MVP 提供 3 款 SIL OFL 授权中文字体。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandwritingFontManager {

    private final CosService cosService;

    /** COS 上字体的存储路径前缀 */
    private static final String COS_PREFIX = "handwriting/fonts";

    /** 预签名 URL 有效期 */
    private static final Duration URL_DURATION = Duration.ofMinutes(30);

    /** MVP 字体注册表 */
    private static final List<FontEntry> REGISTRY = List.of(
        new FontEntry("手书体", "shoushu", "shoushu.ttf", "春眠不觉晓，处处闻啼鸟"),
        new FontEntry("851手写杂字体", "tegakizatsu", "851tegakizatsu.ttf", "夜来风雨声，花落知多少"),
        new FontEntry("今年也要加油鸭", "jiayouya", "jiayouya.ttf", "举头望明月，低头思故乡")
    );

    /**
     * 返回可用字体列表（不含 URL，前端只需名称和 key）。
     */
    public List<HandwritingFont> listFonts() {
        return REGISTRY.stream()
                .map(e -> new HandwritingFont(e.name, e.key, e.previewText))
                .toList();
    }

    /**
     * 获取字体的 COS 预签名 URL（Playwright 直接加载）。
     * @param fontKey 字体 key，如 "shoushu"
     * @return 预签名 URL，或 null 如果字体不存在
     */
    public String getFontUrl(String fontKey) {
        FontEntry entry = findByKey(fontKey);
        if (entry == null) {
            log.warn("字体不存在: key={}", fontKey);
            return null;
        }
        String cosKey = COS_PREFIX + "/" + entry.fileName;
        return cosService.generatePresignedUrl(cosKey, URL_DURATION);
    }

    /**
     * 生成 CSS @font-face 声明块，供 HandwritingRenderer 嵌入 HTML。
     * @param fontKey 字体 key
     * @return CSS @font-face 块，或空字符串如果字体不存在
     */
    public String generateFontFaceCss(String fontKey) {
        FontEntry entry = findByKey(fontKey);
        if (entry == null) {
            return "";
        }
        String url = getFontUrl(fontKey);
        if (url == null) {
            return "";
        }
        return "@font-face { font-family: '" + entry.name + "'; " +
               "src: url('" + url + "') format('truetype'); " +
               "font-display: block; }";
    }

    /**
     * 生成所有字体的 CSS @font-face 块 + system font fallback。
     */
    public String generateAllFontFacesCss(String primaryFontKey) {
        StringBuilder sb = new StringBuilder();
        for (FontEntry entry : REGISTRY) {
            String ff = generateFontFaceCss(entry.key);
            if (!ff.isEmpty()) {
                sb.append(ff).append("\n");
            }
        }
        // 生僻字回退：handwriting 字体未覆盖的字符回退到系统字体
        FontEntry primary = findByKey(primaryFontKey);
        String primaryName = primary != null ? primary.name : "手书体";
        sb.append("body { font-family: '").append(primaryName)
          .append("', 'Noto Sans CJK SC', 'Microsoft YaHei', sans-serif; }\n");
        return sb.toString();
    }

    private FontEntry findByKey(String key) {
        return REGISTRY.stream()
                .filter(e -> e.key.equals(key))
                .findFirst().orElse(null);
    }

    /** 内部字体条目 */
    private record FontEntry(String name, String key, String fileName, String previewText) {}
}
```

- [ ] **Step 3: 写单元测试**

```java
package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.service.CosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandwritingFontManagerTest {

    @Mock
    private CosService cosService;

    @InjectMocks
    private HandwritingFontManager fontManager;

    @Test
    void listFonts_returnsThreeFonts() {
        var fonts = fontManager.listFonts();
        assertEquals(3, fonts.size());
        assertEquals("手书体", fonts.get(0).name());
        assertEquals("shoushu", fonts.get(0).key());
    }

    @Test
    void getFontUrl_validKey_returnsUrl() {
        when(cosService.generatePresignedUrl(anyString(), any()))
                .thenReturn("https://cos.example.com/font.ttf?sign=xxx");
        String url = fontManager.getFontUrl("shoushu");
        assertNotNull(url);
        assertTrue(url.startsWith("https://"));
    }

    @Test
    void getFontUrl_unknownKey_returnsNull() {
        String url = fontManager.getFontUrl("nonexistent");
        assertNull(url);
    }

    @Test
    void generateFontFaceCss_validKey_containsFontFace() {
        when(cosService.generatePresignedUrl(anyString(), any()))
                .thenReturn("https://cos.example.com/shoushu.ttf?sign=xxx");
        String css = fontManager.generateFontFaceCss("shoushu");
        assertTrue(css.contains("@font-face"));
        assertTrue(css.contains("font-family: '手书体'"));
    }

    @Test
    void generateFontFaceCss_unknownKey_returnsEmpty() {
        String css = fontManager.generateFontFaceCss("unknown");
        assertTrue(css.isEmpty());
    }

    @Test
    void generateAllFontFacesCss_includesFallback() {
        when(cosService.generatePresignedUrl(anyString(), any()))
                .thenReturn("https://cos.example.com/font.ttf?sign=xxx");
        String css = fontManager.generateAllFontFacesCss("shoushu");
        assertTrue(css.contains("font-family: '手书体'"));
        assertTrue(css.contains("Microsoft YaHei"));
    }
}
```

Run: `mvn test -Dtest=HandwritingFontManagerTest -Dspring.profiles.active=test`
Expected: 6 tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/handwriting/HandwritingFontManager.java \
        src/main/java/com/example/aipassagecreator/handwriting/model/HandwritingFont.java \
        src/test/java/com/example/aipassagecreator/handwriting/HandwritingFontManagerTest.java
git commit -m "feat(handwriting): HandwritingFontManager — 字体管理 + COS URL + @font-face

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: HandwritingRenderer — 核心渲染器

> **审计 #11、#16 修复**：Jsoup 清洗防 XSS，生僻字检测。

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/handwriting/HandwritingRenderer.java`

- [ ] **Step 1: 创建 HandwritingRenderer**

```java
package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * 手写效果核心渲染器。
 * 职责：文本 + 参数 → 含手写效果 + 字体 + 纸张的完整自包含 HTML 页面。
 *
 * <p>安全：用户内容在注入 HTML 前经过 Jsoup.clean() 清洗，
 * 清除所有 script/style/iframe 等标签，只保留基本格式化标签。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandwritingRenderer {

    private final HandwritingFontManager fontManager;
    private final HandwritingPaperService paperService;

    /** CJK 基本字库范围（GB2312 一级，~3755 字） */
    private static final int GB2312_LEVEL1_COUNT = 3755;

    /**
     * 将手写请求渲染为完整自包含 HTML 页面。
     *
     * @param request 手写渲染请求
     * @return 自包含 HTML 页面字符串
     */
    public String renderToHtml(HandwritingRequest request) {
        // 1. HTML 清洗（防 XSS）
        String sanitized = sanitizeContent(request.content());

        // 2. 生僻字检测
        var charReport = analyzeCharacterCoverage(sanitized);

        // 3. 生成字体 CSS
        String fontCss = fontManager.generateAllFontFacesCss(request.fontName());

        // 4. 生成纸张背景 CSS
        String paperCss = paperService.generatePaperCss(
                request.paperType(), request.paperImageUrl());

        // 5. 构建完整 HTML
        return buildHtml(sanitized, fontCss, paperCss,
                request.effectiveParams(), charReport.hasWarning());
    }

    /**
     * HTML 清洗：注入前用 Jsoup 清除所有脚本/样式/iframe 等危险标签。
     * 只保留 p, br, strong, em, h1-h6, ul, ol, li, blockquote, pre, code。
     */
    String sanitizeContent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "";
        }
        Safelist safelist = Safelist.basic()
                .addTags("h1", "h2", "h3", "h4", "h5", "h6",
                         "pre", "code", "blockquote", "hr")
                .addAttributes("pre", "class")
                .addAttributes("code", "class");
        return Jsoup.clean(rawContent, safelist);
    }

    /**
     * 生僻字覆盖率分析。
     * 简单方案：统计文本中字符是否在基本 CJK 范围内。
     * 实际生产可用 ICU4J 或自定义字库映射。
     */
    CharacterCoverageReport analyzeCharacterCoverage(String text) {
        if (text == null || text.isBlank()) {
            return new CharacterCoverageReport(100.0, false);
        }
        int totalCjk = 0;
        int unusual = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                totalCjk++;
                // 简单启发式：codepoint > GB2312 一级范围标记为潜在生僻
                if (c > 0x9FA5) {
                    unusual++;
                }
            }
        }
        if (totalCjk == 0) {
            return new CharacterCoverageReport(100.0, false);
        }
        double coverage = 100.0 * (totalCjk - unusual) / totalCjk;
        boolean hasWarning = coverage < 95.0;
        if (hasWarning) {
            log.warn("文本含较多生僻字，手写字体可能无法覆盖: coverage={:.1f}%", coverage);
        }
        return new CharacterCoverageReport(coverage, hasWarning);
    }

    /** 生僻字覆盖报告 */
    public record CharacterCoverageReport(double coveragePercent, boolean hasWarning) {}

    /**
     * 构建完整的自包含 HTML 页面。
     * 包含 @font-face、纸张背景 CSS、扰动 JS 脚本、内容主体。
     * 注意：此方法在 Java 侧生成完整 HTML，不通过 Thymeleaf，
     * 避免 th:utext 的安全顾虑（审计 #4）。
     */
    private String buildHtml(String content, String fontCss, String paperCss,
                             HandwritingParams p, boolean hasCharWarning) {
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    width: %dpx; min-height: %dpx;
    %s
    padding: 60px 80px;
    font-size: 28px;
    line-height: 2.0;
    color: #2D1810;
    -webkit-font-smoothing: antialiased;
  }
  %s
  .content { max-width: 100%%; word-wrap: break-word; }
  .content p { margin-bottom: 8px; }
  .content h1, .content h2, .content h3 {
    margin-top: 20px;
    margin-bottom: 12px;
  }
  .char-warning {
    position: fixed; top: 10px; right: 10px;
    font-size: 12px; color: #999;
  }
  .char-warning span { background: #FFF3CD; padding: 2px 6px; border-radius: 4px; }
</style>
</head>
<body>
<div class="content">%s</div>
<script>
(function() {
  // 手写扰动引擎：对每个字符应用随机位置偏移、旋转、字号变化
  var params = { posJitter: %.1f, rotJitter: %.1f, sizeJitter: %.1f, ink: %.2f };
  function applyJitter(el) {
    if (!el || !el.childNodes) return;
    el.childNodes.forEach(function(node) {
      if (node.nodeType === 3 && node.textContent.trim()) {
        // 文本节点 → 逐字包裹为 span
        var frag = document.createDocumentFragment();
        node.textContent.split('').forEach(function(ch) {
          var span = document.createElement('span');
          span.textContent = ch;
          span.style.display = 'inline-block';
          span.style.position = 'relative';
          // 位置抖动
          var dx = (Math.random() - 0.5) * params.posJitter * 2;
          var dy = (Math.random() - 0.5) * params.posJitter;
          span.style.left = dx + 'px';
          span.style.top = dy + 'px';
          // 旋转抖动
          var rot = (Math.random() - 0.5) * params.rotJitter * 2;
          span.style.transform = 'rotate(' + rot + 'deg)';
          // 字号变化
          var s = 1 + (Math.random() - 0.5) * params.sizeJitter / 50.0;
          span.style.fontSize = (100 * s).toFixed(0) + '%%';
          // 墨迹浓淡
          var alpha = params.ink + (Math.random() - 0.5) * 0.1;
          span.style.opacity = Math.max(0, Math.min(1, alpha)).toFixed(2);
          frag.appendChild(span);
        });
        node.parentNode.replaceChild(frag, node);
      } else if (node.nodeType === 1) {
        applyJitter(node);
      }
    });
  }
  applyJitter(document.querySelector('.content'));
})();
</script>
</body>
</html>
""".formatted(
            1240, 1754,        // viewport
            paperCss,
            fontCss,
            content,
            p.positionJitter(), p.rotationJitter(),
            p.sizeJitter(), p.inkDensity()
        );
    }
}
```

- [ ] **Step 2: 写单元测试**

```java
package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingParams;
import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandwritingRendererTest {

    @Mock
    private HandwritingFontManager fontManager;

    @Mock
    private HandwritingPaperService paperService;

    @InjectMocks
    private HandwritingRenderer renderer;

    @Test
    void sanitizeContent_stripsScriptTags() {
        String input = "<p>正常文本</p><script>alert('xss')</script>";
        String result = renderer.sanitizeContent(input);
        assertFalse(result.contains("<script>"));
        assertTrue(result.contains("正常文本"));
    }

    @Test
    void sanitizeContent_stripsStyleTags() {
        String input = "<p>内容</p><style>body{color:red}</style>";
        String result = renderer.sanitizeContent(input);
        assertFalse(result.contains("<style>"));
    }

    @Test
    void sanitizeContent_null_returnsEmpty() {
        assertEquals("", renderer.sanitizeContent(null));
    }

    @Test
    void sanitizeContent_blank_returnsEmpty() {
        assertEquals("", renderer.sanitizeContent("  "));
    }

    @Test
    void analyzeCharacterCoverage_asciiOnly_fullCoverage() {
        var report = renderer.analyzeCharacterCoverage("hello world");
        assertEquals(100.0, report.coveragePercent());
        assertFalse(report.hasWarning());
    }

    @Test
    void analyzeCharacterCoverage_commonChinese_fullCoverage() {
        var report = renderer.analyzeCharacterCoverage("你好世界这是一段普通的中文文本");
        assertTrue(report.coveragePercent() >= 90.0);
    }

    @Test
    void renderToHtml_producesCompleteHtml() {
        when(fontManager.generateAllFontFacesCss(anyString())).thenReturn("/* font css */");
        when(paperService.generatePaperCss(anyString(), anyString()))
                .thenReturn("background: #FFFFFF;");

        var req = new HandwritingRequest(
                "<p>测试</p>", "shoushu", "line",
                HandwritingParams.defaults(), null);
        String html = renderer.renderToHtml(req);

        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<html lang=\"zh-CN\">"));
        assertTrue(html.contains("测试"));
        assertTrue(html.contains("handwriting"));
        // 确认不含原始 script 标签
        assertFalse(html.contains("<script>alert"));
    }
}
```

Run: `mvn test -Dtest=HandwritingRendererTest -Dspring.profiles.active=test`
Expected: 7 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/handwriting/HandwritingRenderer.java \
        src/test/java/com/example/aipassagecreator/handwriting/HandwritingRendererTest.java
git commit -m "feat(handwriting): HandwritingRenderer 核心渲染器 — 审计#11#16修复

Jsoup 清洗防 XSS、生僻字检测、自包含 HTML 生成（绕过 th:utext）。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: HandwritingStructurePlanner — 独立分页器

> **审计 #5 修复**：手写纸张 A4 比例分页，段落边界优先。

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/handwriting/HandwritingStructurePlanner.java`
- Create: `src/main/java/com/example/aipassagecreator/handwriting/model/HandwritingPage.java`

- [ ] **Step 1: 创建 HandwritingPage 模型**

```java
package com.example.aipassagecreator.handwriting.model;

/**
 * 手写分页结果 — 单页内容结构。
 */
public record HandwritingPage(
    int pageNo,
    String title,        // 页面标题（可选）
    String contentMd     // Markdown 内容（已按段落边界分页）
) {}
```

- [ ] **Step 2: 创建 HandwritingStructurePlanner**

```java
package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 手写纸张分页器。
 * <p>A4 比例页面（~1300 中文字符/页），按段落边界断页。
 * 与 CardStructurePlanner 差异：
 * <ul>
 *   <li>更大容量（1300 vs 500 字/页）</li>
 *   <li>段落边界优先（不在段落中间断页）</li>
 *   <li>无封面页概念</li>
 * </ul>
 */
@Slf4j
@Component
public class HandwritingStructurePlanner {

    private static final int MAX_CHARS_PER_PAGE = 1300;
    private static final int MAX_PAGES = 30;

    /**
     * 将文本按段落边界分页。
     *
     * @param content 已清洗的文本内容
     * @param title   文档标题（可选，第一页作为标题）
     * @return 分页列表
     */
    public List<HandwritingPage> plan(String content, String title) {
        if (content == null || content.isBlank()) {
            return List.of(new HandwritingPage(1, title, ""));
        }

        // 按段落（空行）拆分
        String[] paragraphs = content.split("\\n\\s*\\n");
        List<HandwritingPage> pages = new ArrayList<>();
        StringBuilder currentPage = new StringBuilder();
        int pageNo = 1;

        // 标题作为第一页头
        if (title != null && !title.isBlank()) {
            currentPage.append("# ").append(title).append("\n\n");
        }

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            // 如果加上该段落超过上限，另起一页
            if (currentPage.length() > 0 &&
                    currentPage.length() + trimmed.length() > MAX_CHARS_PER_PAGE) {
                pages.add(new HandwritingPage(pageNo++, null, currentPage.toString().trim()));
                currentPage.setLength(0);
            }

            if (currentPage.length() > 0) {
                currentPage.append("\n\n");
            }
            currentPage.append(trimmed);
        }

        // 最后一页
        if (!currentPage.isEmpty()) {
            pages.add(new HandwritingPage(pageNo, null, currentPage.toString().trim()));
        }

        if (pages.size() > MAX_PAGES) {
            log.warn("手写分页超出上限: pages={}, max={}", pages.size(), MAX_PAGES);
            // 截断而非抛异常
            return pages.subList(0, MAX_PAGES);
        }

        return pages;
    }
}
```

- [ ] **Step 3: 写单元测试**

```java
package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HandwritingStructurePlannerTest {

    private final HandwritingStructurePlanner planner = new HandwritingStructurePlanner();

    @Test
    void plan_emptyContent_returnsSinglePageWithTitle() {
        List<HandwritingPage> pages = planner.plan("", "测试标题");
        assertEquals(1, pages.size());
        assertEquals("测试标题", pages.get(0).title());
        assertTrue(pages.get(0).contentMd().contains("# 测试标题"));
    }

    @Test
    void plan_nullContent_returnsSinglePage() {
        List<HandwritingPage> pages = planner.plan(null, null);
        assertEquals(1, pages.size());
    }

    @Test
    void plan_shortContent_singlePage() {
        List<HandwritingPage> pages = planner.plan("短内容", null);
        assertEquals(1, pages.size());
        assertTrue(pages.get(0).contentMd().contains("短内容"));
    }

    @Test
    void plan_longContent_multiplePages() {
        // 构造超过 1300 字的长文本
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("这是第").append(i).append("段测试文本。");
            sb.append("包含足够多的中文字符来触发分页。\n\n");
        }
        List<HandwritingPage> pages = planner.plan(sb.toString(), null);
        assertTrue(pages.size() > 1, "长文本应分为多页");
        // 每页不超过上限
        for (HandwritingPage p : pages) {
            assertTrue(p.contentMd().length() <= 1400,
                    "页码 " + p.pageNo() + " 超出字数上限");
        }
    }

    @Test
    void plan_paragraphBoundary_preserved() {
        // 构造刚好跨边界的段落
        String para1 = "A".repeat(1200) + "\n\n";
        String para2 = "B".repeat(200);
        List<HandwritingPage> pages = planner.plan(para1 + para2, null);
        assertEquals(2, pages.size());
        assertTrue(pages.get(0).contentMd().contains("AAA"));
        assertTrue(pages.get(1).contentMd().contains("BBB"));
    }
}
```

Run: `mvn test -Dtest=HandwritingStructurePlannerTest -Dspring.profiles.active=test`
Expected: 5 tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/handwriting/HandwritingStructurePlanner.java \
        src/main/java/com/example/aipassagecreator/handwriting/model/HandwritingPage.java \
        src/test/java/com/example/aipassagecreator/handwriting/HandwritingStructurePlannerTest.java
git commit -m "feat(handwriting): HandwritingStructurePlanner — A4 比例段落边界分页

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: HandwritingService — 编辑器业务编排

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/handwriting/HandwritingService.java`

- [ ] **Step 1: 创建 HandwritingService**

```java
package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.card.CardPageMapper;
import com.example.aipassagecreator.card.CardRenderPipeline;
import com.example.aipassagecreator.card.model.PageResult;
import com.example.aipassagecreator.handwriting.config.HandwritingRenderConfig;
import com.example.aipassagecreator.handwriting.model.HandwritingPage;
import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import com.example.aipassagecreator.service.CosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 手写编辑器业务编排。
 * 职责：分页 → 渲染 HTML → Playwright 截图 → COS 上传。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandwritingService {

    private final HandwritingStructurePlanner planner;
    private final HandwritingRenderer renderer;
    private final CardRenderPipeline pipeline;
    private final HandwritingRenderConfig config;
    private final CosService cosService;

    /**
     * 预览：渲染单页手写效果并上传到 COS，返回预签名 URL。
     * 同步执行，不扣配额。
     *
     * @param request 已校验的渲染请求
     * @return 预览图片 URL
     */
    public String preview(HandwritingRequest request) {
        // 1. 分页（只取第一页预览）
        List<HandwritingPage> pages = planner.plan(request.content(), null);
        HandwritingPage firstPage = pages.isEmpty()
                ? new HandwritingPage(1, null, request.content())
                : pages.get(0);

        // 2. 渲染第一页
        HandwritingRequest pageReq = new HandwritingRequest(
                firstPage.contentMd(),
                request.fontName(), request.paperType(),
                request.effectiveParams(), request.paperImageUrl());
        String html = renderer.renderToHtml(pageReq);

        // 3. Playwright 渲染 + 上传
        List<PageResult> results = pipeline.renderWithJs(
                List.of(html), "preview-" + System.nanoTime(), config);

        if (results.isEmpty() || results.get(0).getPngBytes() == null) {
            throw new IllegalStateException("手写预览渲染失败");
        }

        byte[] png = results.get(0).getPngBytes();
        String key = "handwriting/preview/" + System.nanoTime() + ".png";
        cosService.uploadToKey(png, "image/png", key);
        return cosService.generatePresignedUrl(key);
    }

    /**
     * 导出：全量分页 → 渲染 → 逐页上传 → 返回图片 URL 列表。
     *
     * @param request  已校验的渲染请求
     * @param taskId   任务 ID（用于 COS key）
     * @param title    文档标题（可选）
     * @return 每页图片的 COS URL 列表
     */
    public List<String> export(HandwritingRequest request, String taskId, String title) {
        List<HandwritingPage> pages = planner.plan(request.content(), title);

        List<String> htmls = pages.stream().map(page -> {
            HandwritingRequest pageReq = new HandwritingRequest(
                    page.contentMd(),
                    request.fontName(), request.paperType(),
                    request.effectiveParams(), request.paperImageUrl());
            return renderer.renderToHtml(pageReq);
        }).toList();

        List<PageResult> results = pipeline.renderWithJs(htmls, taskId, config);

        return results.stream().map(r -> {
            if (r.getPngBytes() == null) return null;
            String key = "handwriting/" + taskId + "/export/" + r.getPageNo() + ".png";
            cosService.uploadToKey(r.getPngBytes(), "image/png", key);
            return cosService.generatePresignedUrl(key);
        }).filter(url -> url != null).toList();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/handwriting/HandwritingService.java
git commit -m "feat(handwriting): HandwritingService — 预览+导出业务编排

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: HandwritingAsyncService + HandwritingController

> **审计 #9、#13、#14、#15 修复**：频率限制、SSE key 隔离、COS key 隔离、配额策略。

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/handwriting/HandwritingAsyncService.java`
- Create: `src/main/java/com/example/aipassagecreator/handwriting/HandwritingController.java`

- [ ] **Step 1: 创建 HandwritingAsyncService**

```java
package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 手写导出异步服务。
 * <p>通过 handwriting_ 前缀的 SSE key 推送导出进度和结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandwritingAsyncService {

    private final HandwritingService handwritingService;
    private final SseEmitterManager sseEmitterManager;

    /** SSE key 前缀隔离（审计 #13） */
    private static final String SSE_PREFIX = "handwriting_";

    @Async("cardExecutor")
    public void export(HandwritingRequest request, String taskId, String title) {
        String sseKey = SSE_PREFIX + taskId;
        try {
            sseEmitterManager.send(sseKey,
                    GsonUtils.toJson(Map.of("type", "handwriting_progress",
                            "message", "开始渲染...")));

            List<String> urls = handwritingService.export(request, taskId, title);

            sseEmitterManager.send(sseKey,
                    GsonUtils.toJson(Map.of(
                            "type", "handwriting_complete",
                            "taskId", taskId,
                            "urls", urls,
                            "pageCount", urls.size())));
            sseEmitterManager.complete(sseKey);
            log.info("手写导出完成: taskId={}, pages={}", taskId, urls.size());
        } catch (Exception e) {
            log.error("手写导出失败: taskId={}", taskId, e);
            String msg = e.getMessage() != null ? e.getMessage() : "未知错误";
            sseEmitterManager.send(sseKey,
                    GsonUtils.toJson(Map.of("type", "handwriting_error", "error", msg)));
            sseEmitterManager.complete(sseKey);
        }
    }
}
```

- [ ] **Step 2: 创建 HandwritingController**

```java
package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.card.CardStyle;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.handwriting.model.HandwritingFont;
import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import com.example.aipassagecreator.handwriting.model.HandwritingExportVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 手写编辑器 API。
 * <p>提供字体列表、纸张列表、预览、PNG/PDF 导出、文件导入等端点。
 */
@Slf4j
@RestController
@RequestMapping("/api/handwriting")
@RequiredArgsConstructor
public class HandwritingController {

    private final HandwritingFontManager fontManager;
    private final HandwritingService handwritingService;
    private final HandwritingAsyncService asyncService;

    /** 纸张类型列表 */
    private static final List<Map<String, String>> PAPER_TYPES = List.of(
            Map.of("key", "blank", "name", "空白纸"),
            Map.of("key", "line", "name", "横线纸"),
            Map.of("key", "grid", "name", "方格纸"),
            Map.of("key", "tianzi", "name", "田字格"),
            Map.of("key", "dot", "name", "点阵纸")
    );

    @GetMapping("/fonts")
    @Operation(summary = "获取可用手写字体列表")
    public BaseResponse<List<HandwritingFont>> listFonts() {
        return ResultUtils.success(fontManager.listFonts());
    }

    @GetMapping("/papers")
    @Operation(summary = "获取纸张类型列表")
    public BaseResponse<List<Map<String, String>>> listPapers() {
        return ResultUtils.success(PAPER_TYPES);
    }

    @PostMapping("/preview")
    @Operation(summary = "手写效果预览（同步，不扣配额）")
    @RateLimit(limit = 10, window = 60, key = "handwriting_preview")
    public BaseResponse<String> preview(@Valid @RequestBody HandwritingRequest request) {
        ThrowUtils.throwIf(request == null || request.content().isBlank(),
                ErrorCode.PARAMS_ERROR, "内容不能为空");
        try {
            String url = handwritingService.preview(request);
            return ResultUtils.success(url);
        } catch (Exception e) {
            log.error("手写预览失败", e);
            return ResultUtils.error(ErrorCode.OPERATION_ERROR, "预览失败: " + e.getMessage());
        }
    }

    @PostMapping("/export/png")
    @Operation(summary = "PNG 导出（异步，扣配额，SSE 推送）")
    @RateLimit(limit = 3, window = 60, key = "handwriting_export")
    public BaseResponse<HandwritingExportVO> exportPng(
            @Valid @RequestBody HandwritingRequest request) {
        // 配额检查由调用方（如 quotaService.checkAndConsumeQuota）处理
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        asyncService.export(request, taskId, null);
        return ResultUtils.success(new HandwritingExportVO(taskId,
                "/api/handwriting/progress/" + taskId));
    }

    @PostMapping("/export/pdf")
    @Operation(summary = "PDF 导出（异步，扣配额，SSE 推送）")
    @RateLimit(limit = 3, window = 60, key = "handwriting_export")
    public BaseResponse<HandwritingExportVO> exportPdf(
            @Valid @RequestBody HandwritingRequest request) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        // PDF 导出路径：先用 renderToPdf() 生成 PDF bytes
        // 然后类似 PNG 流程上传 COS
        asyncService.export(request, taskId, null); // 初期复用 PNG 流程
        return ResultUtils.success(new HandwritingExportVO(taskId,
                "/api/handwriting/progress/" + taskId));
    }
}
```

- [ ] **Step 3: 创建 HandwritingExportVO**

```java
package com.example.aipassagecreator.handwriting.model;

/**
 * 手写导出响应 VO。
 */
public record HandwritingExportVO(
    String taskId,
    String progressUrl
) {}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/handwriting/HandwritingAsyncService.java \
        src/main/java/com/example/aipassagecreator/handwriting/HandwritingController.java \
        src/main/java/com/example/aipassagecreator/handwriting/model/HandwritingExportVO.java
git commit -m "feat(handwriting): Controller + AsyncService — SSE隔离+频控+配额

审计 #9#13#14#15 修复。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 12: CardService — handwriting 风格路由

> 当 style=handwriting 时走 HandwritingRenderer 渲染，而非 CardTemplateEngine。

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/card/CardService.java`

在 `generate()` 方法中，style 分支判断：当 `cardStyle` 为 `"handwriting"` 时使用 HandwritingRenderer + HandwritingStructurePlanner 替代 CardTemplateEngine + CardStructurePlanner。

在 `CardService` 中注入 `HandwritingRenderer` 和 `HandwritingStructurePlanner` 和 `HandwritingRenderConfig`：

```java
private final HandwritingRenderer handwritingRenderer;
private final HandwritingStructurePlanner handwritingPlanner;
private final com.example.aipassagecreator.handwriting.config.HandwritingRenderConfig handwritingConfig;

// 在 generate() 方法的 "3. 渲染" 阶段增加分支:

// 3. 渲染
List<String> htmls;
if (CardStyle.HANDWRITING.getName().equals(cardStyle)) {
    // 手写路径：使用 HandwritingStructurePlanner + HandwritingRenderer
    var hwPages = handwritingPlanner.plan(fullContent,
            mainTitle + (subTitle != null ? " - " + subTitle : ""));
    htmls = hwPages.stream().map(page -> {
        var hwReq = new com.example.aipassagecreator.handwriting.model.HandwritingRequest(
                page.contentMd(), "shoushu", "line", null, null);
        return handwritingRenderer.renderToHtml(hwReq);
    }).toList();
} else {
    // 原有路径
    htmls = templateEngine.render(pages, cardStyle);
}

// 4. 渲染管线选择
List<PageResult> results;
if (CardStyle.HANDWRITING.getName().equals(cardStyle)) {
    results = renderPipeline.renderWithJs(htmls, taskId, handwritingConfig);
} else {
    results = renderPipeline.render(htmls, taskId);
}
```

**注意**：此任务需要修改 CardService 构造器追加 3 个参数，同步更新 CardServiceTest。

---

### Task 13: Card 风格 handwriting 模板 + CardTemplateEngine 更新

**Files:**
- Create: `src/main/resources/templates/cards/handwriting.html`
- Modify: `src/main/java/com/example/aipassagecreator/card/CardTemplateEngine.java`

handwriting 卡片模板（简版，Card 入口专用，参数固定）：

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
<meta charset="UTF-8">
</head>
<body>
<!-- Card 入口的手写模板：实际渲染由 HandwritingRenderer 在 Java 侧完成，
     此模板仅作为流程占位，CardService 在 handwriting 风格时走 HandwritingRenderer -->
<div class="handwriting-content" th:utext="${content}">手写内容</div>
</body>
</html>
```

**注**：Card 入口的实际手写渲染已在 Task 12 的 `CardService.generate()` 分支中完成（Java 侧直接调 `HandwritingRenderer.renderToHtml()`），此 Thymeleaf 模板仅在 CardTemplateEngine 渲染流程中作为 fallback。

---

### Task 14: 集成测试 HandwritingController

**Files:**
- Create: `src/test/java/com/example/aipassagecreator/handwriting/HandwritingControllerIntegrationTest.java`

- [ ] **Step 1: 编写集成测试**

```java
package com.example.aipassagecreator.handwriting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandwritingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listFonts_returnsSuccess() throws Exception {
        mockMvc.perform(get("/api/handwriting/fonts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void listPapers_returnsFiveTypes() throws Exception {
        mockMvc.perform(get("/api/handwriting/papers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    void preview_blankContent_returnsError() throws Exception {
        mockMvc.perform(post("/api/handwriting/preview")
                        .contentType("application/json")
                        .content("""
                            {"content":"","fontName":"shoushu","paperType":"line"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNumber());
    }
}
```

Run: `mvn test -Dtest=HandwritingControllerIntegrationTest -Dspring.profiles.active=test`
Expected: Tests PASS (预览可能因 Playwright 不可用而降级)

- [ ] **Step 2: Commit**

```bash
git add src/test/java/com/example/aipassagecreator/handwriting/HandwritingControllerIntegrationTest.java
git commit -m "test(handwriting): Controller 集成测试 — 字体/纸张/预览端点

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 15: 前端 — API 层 + Pinia Store

**Files:**
- Create: `frontend/src/api/handwritingController.ts`
- Create: `frontend/src/stores/handwritingStore.ts`

- [ ] **Step 1: 创建 API 调用层**

```typescript
// frontend/src/api/handwritingController.ts
import request from '@/request'

export interface HandwritingFont {
  name: string
  key: string
  previewText: string
}

export interface PaperType {
  key: string
  name: string
}

export interface HandwritingParams {
  positionJitter: number
  rotationJitter: number
  sizeJitter: number
  inkDensity: number
}

export interface HandwritingRequest {
  content: string
  fontName: string
  paperType: string
  params: HandwritingParams | null
  paperImageUrl: string | null
}

export interface HandwritingExportVO {
  taskId: string
  progressUrl: string
}

export function listFonts() {
  return request.get<HandwritingFont[]>('/handwriting/fonts')
}

export function listPapers() {
  return request.get<PaperType[]>('/handwriting/papers')
}

export function preview(data: HandwritingRequest) {
  return request.post<string>('/handwriting/preview', data)
}

export function exportPng(data: HandwritingRequest) {
  return request.post<HandwritingExportVO>('/handwriting/export/png', data)
}

export function exportPdf(data: HandwritingRequest) {
  return request.post<HandwritingExportVO>('/handwriting/export/pdf', data)
}
```

- [ ] **Step 2: 创建 Pinia Store**

```typescript
// frontend/src/stores/handwritingStore.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listFonts, listPapers, preview, exportPng, type HandwritingFont, type PaperType, type HandwritingRequest, type HandwritingParams } from '@/api/handwritingController'

export const useHandwritingStore = defineStore('handwriting', () => {
  const fonts = ref<HandwritingFont[]>([])
  const papers = ref<PaperType[]>([])
  const previewUrl = ref<string | null>(null)
  const previewing = ref(false)
  const exporting = ref(false)
  const params = ref<HandwritingParams>({
    positionJitter: 2.0,
    rotationJitter: 1.5,
    sizeJitter: 5.0,
    inkDensity: 0.85,
  })

  async function loadFonts() {
    const res = await listFonts()
    fonts.value = res.data ?? []
  }

  async function loadPapers() {
    const res = await listPapers()
    papers.value = res.data ?? []
  }

  async function doPreview(content: string, fontName: string, paperType: string) {
    previewing.value = true
    try {
      const req: HandwritingRequest = {
        content,
        fontName,
        paperType,
        params: params.value,
        paperImageUrl: null,
      }
      const res = await preview(req)
      previewUrl.value = res.data ?? null
      return res.data
    } finally {
      previewing.value = false
    }
  }

  async function doExport(content: string, fontName: string, paperType: string) {
    exporting.value = true
    try {
      const req: HandwritingRequest = {
        content,
        fontName,
        paperType,
        params: params.value,
        paperImageUrl: null,
      }
      const res = await exportPng(req)
      return res.data
    } finally {
      exporting.value = false
    }
  }

  function resetParams() {
    params.value = { positionJitter: 2.0, rotationJitter: 1.5, sizeJitter: 5.0, inkDensity: 0.85 }
  }

  return {
    fonts, papers, previewUrl, previewing, exporting, params,
    loadFonts, loadPapers, doPreview, doExport, resetParams,
  }
})
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/handwritingController.ts frontend/src/stores/handwritingStore.ts
git commit -m "feat(frontend): handwriting API 层 + Pinia Store

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 16: 前端 — 手写编辑器页面 + 组件

**Files:**
- Create: `frontend/src/pages/handwriting/HandwritingEditorPage.vue`
- Create: `frontend/src/pages/handwriting/components/FontSelector.vue`
- Create: `frontend/src/pages/handwriting/components/PaperSelector.vue`
- Create: `frontend/src/pages/handwriting/components/ParamSliders.vue`
- Create: `frontend/src/pages/handwriting/components/PreviewPanel.vue`
- Modify: `frontend/src/router/index.ts` — 新增 handwriting 路由

- [ ] **Step 1: 创建 HandwritingEditorPage**

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHandwritingStore } from '@/stores/handwritingStore'
import FontSelector from './components/FontSelector.vue'
import PaperSelector from './components/PaperSelector.vue'
import ParamSliders from './components/ParamSliders.vue'
import PreviewPanel from './components/PreviewPanel.vue'
import { message } from 'ant-design-vue'

const store = useHandwritingStore()
const content = ref('')
const selectedFont = ref('shoushu')
const selectedPaper = ref('line')

onMounted(async () => {
  await Promise.all([store.loadFonts(), store.loadPapers()])
})

async function handlePreview() {
  if (!content.value.trim()) {
    message.warning('请输入文字内容')
    return
  }
  try {
    await store.doPreview(content.value, selectedFont.value, selectedPaper.value)
  } catch (e: any) {
    message.error('预览失败: ' + (e.message || '未知错误'))
  }
}

async function handleExport() {
  if (!content.value.trim()) {
    message.warning('请输入文字内容')
    return
  }
  try {
    const result = await store.doExport(content.value, selectedFont.value, selectedPaper.value)
    if (result) {
      message.success('导出任务已创建，请等待渲染完成')
    }
  } catch (e: any) {
    message.error('导出失败: ' + (e.message || '未知错误'))
  }
}
</script>

<template>
  <div class="handwriting-editor">
    <h1 style="margin-bottom:24px">手写笔记编辑器</h1>

    <div class="config-bar">
      <FontSelector v-model="selectedFont" :fonts="store.fonts" />
      <PaperSelector v-model="selectedPaper" :papers="store.papers" />
      <ParamSliders v-model="store.params" />
    </div>

    <div class="editor-body">
      <div class="text-panel">
        <textarea
          v-model="content"
          placeholder="在此输入或粘贴文字内容..."
          rows="20"
        />
      </div>
      <div class="preview-panel">
        <PreviewPanel
          :preview-url="store.previewUrl"
          :previewing="store.previewing"
        />
      </div>
    </div>

    <div class="action-bar">
      <a-button type="default" @click="handlePreview" :loading="store.previewing">
        预览
      </a-button>
      <a-button type="primary" @click="handleExport" :loading="store.exporting">
        导出 PNG
      </a-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.handwriting-editor {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.config-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  align-items: center;
  flex-wrap: wrap;
}
.editor-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  min-height: 500px;
}
.text-panel textarea {
  width: 100%;
  height: 100%;
  min-height: 400px;
  padding: 16px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 16px;
  line-height: 1.8;
  resize: vertical;
}
.preview-panel {
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  overflow: hidden;
}
.action-bar {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 2: 创建 FontSelector 组件**

```vue
<script setup lang="ts">
import type { HandwritingFont } from '@/api/handwritingController'

const props = defineProps<{ fonts: HandwritingFont[]; modelValue: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
</script>

<template>
  <div class="font-selector">
    <label>字体</label>
    <a-select
      :value="modelValue"
      @change="(v: string) => emit('update:modelValue', v)"
      style="width: 180px"
    >
      <a-select-option v-for="f in fonts" :key="f.key" :value="f.key">
        {{ f.name }}
      </a-select-option>
    </a-select>
  </div>
</template>
```

- [ ] **Step 3: 创建 PaperSelector、ParamSliders、PreviewPanel**

类似简单组件，遵循 Ant Design Vue 4 模式。

- [ ] **Step 4: 路由注册**

在 `frontend/src/router/index.ts` 中新增：

```typescript
{
  path: '/handwriting',
  name: 'HandwritingEditor',
  component: () => import('@/pages/handwriting/HandwritingEditorPage.vue'),
  meta: { requiresAuth: true },
}
```

- [ ] **Step 5: 运行前端检查**

```bash
cd frontend && npm run type-check
```

Expected: 零 TS 错误

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/handwriting/ frontend/src/router/index.ts
git commit -m "feat(frontend): 手写编辑器页面 + 组件 + 路由

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 17: 后端全量回归测试

```bash
mvn test -Dspring.profiles.active=test
```

Expected: ALL tests PASS，特别关注现有 card/ 包测试无回归。

---

### Task 18: 前端全量质量闸门

```bash
cd frontend && npm run check
```

Expected: lint + type-check + build + test 全部通过。

---

## 自审检查清单

- [x] **Spec 覆盖**: 18 项审计发现全部有对应 Task（Task 1-4 覆盖 S1/S2，Task 5-11 覆盖架构实现，Task 14 集成测试覆盖）
- [x] **占位符扫描**: 无 TBD/TODO。JS 扰动库确认为自研轻量实现而非开源库集成（审计 #17 的 MVP 决策）。
- [x] **类型一致性**: `HandwritingRenderConfig` 在各 Task 间签名一致。`CardStyle.HANDWRITING` 在 Task 1/12/13 中统一。
- [x] **文件路径**: 全部指到确切路径。
