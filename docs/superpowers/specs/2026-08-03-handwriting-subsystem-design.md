# 手写效果子系统 — 设计文档

> **日期**: 2026-08-03
> **状态**: 已审批
> **版本**: 1.0

---

## 一、背景与目标

### 1.1 需求来源

集成 [字映 ScriptEcho (s.owwa.dev)](https://s.owwa.dev/) 的手写效果能力到 ai-passage-creator 平台。

**核心定位**：将 AI 生成的文章内容转化为具有真实手写质感的图片，支持四大场景：

| 场景 | 典型用户 | 用途 |
|------|---------|------|
| 社交媒体手写笔记分享 | 自媒体/博主 | 小红书/朋友圈手写笔记风配图 |
| 学生作业/笔记场景 | 学生群体 | AI 辅助写作 → 手写体输出，打印提交 |
| 卡片系统 handwriting 风格 | 现有用户 | 与 warm/minimal/free 并列的第 4 种卡片风格 |
| 独立手写编辑器 | 进阶用户 | 自由排版、参数精细控制、模板选择 |

### 1.2 能力分层规划

```
Layer 0: 字体模拟（ScriptEcho 级别）        ← MVP 立即交付
  └─ CSS @font-face + 扰动 + 纸张
  └─ YerFont 思想：多 glyph 随机替换（架构预留）

Layer 1: SVG 笔画路径                      ← 第二阶段
  └─ 白板手写动画、教育 PPT

Layer 2: AI 手写生成（LSTM/Diffusion）      ← 第三阶段
  └─ One-DM / DiffusionPen 风格

Layer 3: 个人笔迹克隆                       ← 终极目标
  └─ 上传样本 → 专属字体/模型
```

**MVP 范围**: Layer 0 全量交付。

---

## 二、架构设计

### 2.1 整体架构：共享引擎 + 双入口

```
┌─────────────────────────────────────────────────────────────┐
│                        handwriting/                          │
│  ┌─────────────────────────────────────────────────────┐    │
│  │          共享引擎 (Service Layer)                     │    │
│  │                                                      │    │
│  │  HandwritingRenderer        HandwritingFontManager   │    │
│  │  HandwritingPaperService    HandwritingConfig         │    │
│  │  HandwritingStructurePlanner (独立分页)               │    │
│  └──────────┬──────────────────────┬───────────────────┘    │
│             │                      │                         │
│    ┌────────▼────────┐    ┌───────▼──────────┐              │
│    │  Card 入口       │    │  Editor 入口      │              │
│    │  "handwriting"   │    │  Handwriting      │              │
│    │  卡片风格         │    │  Controller       │              │
│    │  (零新Controller) │    │  /api/handwriting │              │
│    └────────┬─────────┘    └───────┬───────────┘              │
│             │                      │                         │
│    ┌────────▼──────────────────────▼───────────┐              │
│    │     CardRenderPipeline (Playwright)        │              │
│    │   renderWithJs() — 新方法，放行 COS URL    │              │
│    │   HTML + 手写JS库 + 字体(外部) → PNG/PDF   │              │
│    └──────────────────┬────────────────────────┘              │
│                       │                                      │
│              ┌────────▼────────┐                              │
│              │   COS 上传       │                              │
│              │   + 预签名URL    │                              │
│              └────────┬────────┘                              │
│                       │                                      │
│         ┌─────────────▼──────────────┐                        │
│         │  前端消费                    │                        │
│         │  Card 完成态 / Editor 预览   │                        │
│         └────────────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心原则

1. **`HandwritingRenderer` 是唯一的渲染能力提供方** — 封装手写 JS 库，生成含手写效果的 HTML
2. **Card 风格和独立编辑器是消费者，不是复制品**
3. **复用 `CardRenderPipeline`**，通过新增 `renderWithJs()` 方法支持 JS 启用的渲染
4. **所有新增类放在 `handwriting/` 包下**，与 `card/` 平级
5. **不破坏现有卡片管线的安全约束** — JS 启用仅在手写渲染上下文

---

## 三、组件详细设计

### 3.1 HandwritingRenderer（核心渲染器）

```java
package com.example.aipassagecreator.handwriting;

/**
 * 手写效果核心渲染器。
 * 职责：文本 + 参数 → 含手写效果的完整 HTML 页面。
 * 
 * 渲染策略：生成自包含 HTML 页面，内嵌：
 *   1. 开源手写 JS 扰动库
 *   2. CSS @font-face → COS 字体文件
 *   3. 纸张背景 CSS
 *   4. 预格式化文本（HTML 清洗后）
 */
@Component
public class HandwritingRenderer {

    /**
     * @param request 渲染请求（内容 + 字体 + 纸张 + 扰动参数）
     * @return 完整自包含 HTML 页面字符串
     */
    public String renderToHtml(HandwritingRequest request) { ... }
    
    /**
     * HTML 清洗：在注入模板前用 Jsoup 清除所有脚本/样式标签。
     * 只保留基本格式化标签（p, br, strong, em, h1-h6）。
     */
    String sanitizeContent(String rawContent) { ... }
}

// 请求/参数模型
public record HandwritingRequest(
    String content,           // 已清洗的文本内容
    String fontName,          // 字体名称
    String paperType,         // blank/line/grid/tianzi/dot
    HandwritingParams params, // 扰动参数
    String paperImageUrl      // 自定义纸张背景图（可选）
) {}

public record HandwritingParams(
    double positionJitter,    // 位置随机偏移 (px), 默认 2.0
    double rotationJitter,    // 旋转随机角度 (°), 默认 1.5
    double sizeJitter,        // 字号随机变化 (%), 默认 5.0
    double inkDensity         // 墨迹浓淡 (0.0-1.0), 默认 0.85
) {}
```

**关键技术决策**:

| 点 | 决策 | 理由 |
|-----|------|------|
| JS 扰动库 | 调研开源方案（handwriting.js / text-to-handwriting），选最成熟的集成 | 避免自研算法的高风险 |
| HTML 清洗 | 注入前 `Jsoup.clean(content, Safelist.basic())` | 解决审计 #11 XSS 风险 |
| 模板方式 | 不通过 Thymeleaf，直接在 Java 侧生成完整 HTML | 避免 `th:utext` 的安全顾虑 |
| 生僻字检测 | 渲染前统计覆盖率 >95%，低于阈值标记 warning | 解决审计 #16 |

### 3.2 HandwritingFontManager（字体管理器）

```java
@Component
public class HandwritingFontManager {
    
    // 字体存 COS → Playwright 通过 URL 加载
    // COS 路径: handwriting/fonts/{fontName}.ttf
    
    public List<HandwritingFont> listFonts() { ... }
    public String generateFontFaceCss(String fontName, String fontUrl) { ... }
    public String getFontUrl(String fontName) { ... }  // COS 预签名 URL
    
    // MVP 字体列表（SIL OFL 授权）：
    //   1. 手书体 (Shoushu)
    //   2. 851手写杂字体 (851tegakizatsu)
    //   3. 今年也要加油鸭 (JinnianYeYaoJiaYouYa)
}
```

**字体许可证合规**（审计 #10）:
- MVP 仅使用 SIL OFL 授权字体
- 字体文件 + LICENSE.txt 归档在 `resources/handwriting/fonts/`
- 部署时上传到 COS `handwriting/fonts/` 目录

### 3.3 HandwritingPaperService（纸张背景服务）

```java
@Component
public class HandwritingPaperService {
    
    /**
     * 生成纸张背景 CSS。
     * 纯 CSS 实现（repeating-linear-gradient），保证高清渲染。
     */
    public String generatePaperCss(String paperType, String customImageUrl) {
        return switch (paperType) {
            case "blank"  -> blankCss();
            case "line"   -> lineCss();    // 横线纸
            case "grid"   -> gridCss();    // 方格纸
            case "tianzi" -> tianziCss();  // 田字格（虚线十字）
            case "dot"    -> dotCss();      // 点阵纸
            default       -> blankCss();
        };
    }
    
    /** 横线纸：水平 repeating-linear-gradient */
    private String lineCss() {
        return """
            background-image: repeating-linear-gradient(
                transparent, transparent 39px, #B8C6DB 39px, #B8C6DB 40px
            );
            background-size: 100% 40px;
            line-height: 40px;
            """;
    }
    
    // ... 其他纸张类型实现
}
```

### 3.4 HandwritingStructurePlanner（独立分页器）

```java
@Component
public class HandwritingStructurePlanner {
    
    // A4 比例页面，每页约 1200-1500 中文字符
    private static final int MAX_CHARS_PER_PAGE = 1300;
    
    // 维护段落边界，不在段落中间断页
    public List<HandwritingPage> plan(String content, String title) { ... }
}
```

**与 `CardStructurePlanner` 的差异**（审计 #5）:

| 维度 | CardStructurePlanner | HandwritingStructurePlanner |
|------|---------------------|----------------------------|
| 页面比例 | 1080×1920 (9:16) | 1240×1754 (A4 ≈ 1:1.414) |
| 每页字数 | ~500 | ~1300 |
| 分页断点 | `##` 标题 + 句号 | 段落边界优先 |
| 封面页 | 有（主标题+副标题+封面图） | 可选（标题 + 首段内容） |

### 3.5 CardRenderPipeline 改造

**新增方法**（解决审计 #1、#2、#7、#8、#12）:

```java
/**
 * 手写效果渲染（JS 启用 + COS URL 白名单路由）。
 * 与现有 render() 并行存在，不动原管线。
 * 
 * @param htmls        手写 HTML 列表
 * @param taskId       任务 ID
 * @param config       手写渲染配置（COS base URL 等）
 */
public List<PageResult> renderWithJs(List<String> htmls, String taskId, 
                                       HandwritingRenderConfig config) {
    // 1. 路由：只放行 COS 域名，abort 其他
    //    page.route(url -> !url.startsWith(config.cosBaseUrl()), 
    //               route -> route.abort())
    // 2. JS 启用：setJavaScriptEnabled(true)
    // 3. 字体缓存：复用 BrowserContext（不每页 newContext）
    // 4. 超时：RENDER_TIMEOUT 30s, BATCH_TIMEOUT 120s
    // 5. 视口：1240×1754 (A4)
    // 6. 复用现有 Semaphore(3) 并发控制
}

public record HandwritingRenderConfig(
    String cosBaseUrl,      // COS 域名白名单
    int renderTimeoutSec,   // 默认 30
    int batchTimeoutSec,    // 默认 120
    int viewportWidth,      // 默认 1240
    int viewportHeight      // 默认 1754
) {}
```

**PDF 导出**（解决审计 #18）:

```java
/**
 * PDF 导出：Playwright page → PDF byte[]。
 * 用于手写编辑器导出 PDF 格式。
 */
public byte[] renderToPdf(String html, HandwritingRenderConfig config) {
    // page.pdf() with A4 format
}
```

### 3.6 CardService 改造

**幂等删除修复**（解决审计 #3）:

```java
// 旧代码（有 bug）
cardPageMapper.deleteByQuery(
    QueryWrapper.create().eq("task_id", taskId));

// 新代码（增加 style 过滤条件）
cardPageMapper.deleteByQuery(
    QueryWrapper.create()
        .eq("task_id", taskId)
        .eq("style", cardStyle));  // ← 只删同风格
```

### 3.7 风格管理收敛

**CardStyle 枚举**（解决审计 #6）:

```java
public enum CardStyle {
    WARM("warm"),
    MINIMAL("minimal"),
    FREE("free"),
    HANDWRITING("handwriting");  // ← 新增
    
    // 统一的白名单 + 默认值管理
    public static CardStyle from(String name) {
        for (CardStyle s : values()) {
            if (s.name.equalsIgnoreCase(name)) return s;
        }
        return WARM; // 默认回退
    }
}
```

`CardTemplateEngine` 和 `CardController` 中的硬编码 `"warm"` 全部替换为 `CardStyle.WARM.getName()`。

### 3.8 双入口设计

#### 入口一：Card 风格 handwriting

```
用户路径:
  文章完成 → 选择「手写笔记」风格 → CardController.generate()
  → CardService 分页 + HandwritingRenderer 渲染
  → CardRenderPipeline.renderWithJs() → PNG → COS → SSE 推送

改动清单:
  - CardStyle 枚举加 handwriting
  - CardTemplateEngine: SUPPORTED_STYLES 加 handwriting
  - 新增 templates/cards/handwriting.html（简版，仅调用 renderer）
  - CardService: 当 style=handwriting 时使用 HandwritingStructurePlanner + renderWithJs()
  - 前端: 卡片风格选择器增加「手写笔记」选项
```

#### 入口二：独立手写编辑器

```
API 设计:
  GET  /api/handwriting/fonts        — 可用字体列表
  GET  /api/handwriting/templates    — 预设模板列表
  GET  /api/handwriting/papers       — 纸张类型列表
  POST /api/handwriting/preview      — 单页预览（同步，不扣配额）
  POST /api/handwriting/export/png   — PNG 导出（异步，扣配额，SSE 推送）
  POST /api/handwriting/export/pdf   — PDF 导出（异步，扣配额，SSE 推送）
  POST /api/handwriting/import       — DOCX/TXT 文件导入 → 返回文本

SSE Key 隔离（审计 #13）:
  - 卡片生成: taskId (保持不变)
  - 手写编辑器导出: "handwriting_" + exportId

COS Key 隔离（审计 #14）:
  - 卡片:  cards/{taskId}/{pageNo}.png
  - 手写预览: handwriting/{taskId}/preview/{pageNo}.png
  - 手写导出: handwriting/{taskId}/export/{pageNo}.png
```

**前端编辑器布局**:

```
┌─────────────────────────────────────────────────────────┐
│  [字体: 手书体 ▼] [纸张: 横线纸 ▼] [模板: 课堂笔记 ▼]     │
│  [位置扰动 ▬▬▬▬○ 2.0] [旋转 ▬▬○ 1.5]                    │
│  [字号变化 ▬▬▬▬○ 5.0] [墨迹浓淡 ▬▬▬▬▬▬○ 0.85]          │
├──────────────────────┬──────────────────────────────────┤
│   文字编辑区           │     实时预览区                     │
│                       │                                  │
│   (Textarea)          │    (手写效果预览图片)               │
│                       │                                  │
│   [导入 DOCX/TXT]     │                                  │
│                       │                                  │
├──────────────────────┴──────────────────────────────────┤
│  [预览] [导出 PNG] [导出 PDF] [分享到平台]                  │
└─────────────────────────────────────────────────────────┘
```

**配额策略**（审计 #15）:

| 操作 | 扣配额 | 说明 |
|------|--------|------|
| 预览 | ❌ | 交互式编辑器核心体验，免费调用 |
| Card 风格 handwriting | ✅ | 与现有卡片生成一致 |
| 编辑器导出 PNG/PDF | ✅ | 每次导出扣 1 配额 |
| 字体列表/纸张列表/模板列表 | ❌ | 只读查询 |

**频率限制**（审计 #9）:

```java
@PostMapping("/preview")
@RateLimit(limit = 10, window = 60, key = "handwriting_preview")
public BaseResponse<String> preview(...) { ... }

@PostMapping("/export/png")
@RateLimit(limit = 3, window = 60, key = "handwriting_export")
public BaseResponse<HandwritingExportVO> exportPng(...) { ... }
```

---

## 四、数据库变更

### 4.1 无新增表

`article_card` 表复用现有结构，通过 `style = 'handwriting'` 区分。

### 4.2 无 schema 变更

现有 `article_card.style` 列已存在，无需 DDL。

---

## 五、文件清单

### 后端新增文件

```
handwriting/
├── HandwritingRenderer.java           # 核心渲染器
├── HandwritingFontManager.java        # 字体管理
├── HandwritingPaperService.java       # 纸张背景
├── HandwritingStructurePlanner.java   # 独立分页
├── HandwritingController.java         # 编辑器 API
├── HandwritingService.java            # 编辑器业务编排
├── HandwritingAsyncService.java       # 导出异步编排
├── model/
│   ├── HandwritingRequest.java        # 渲染请求
│   ├── HandwritingParams.java         # 扰动参数
│   ├── HandwritingFont.java           # 字体 VO
│   ├── HandwritingTemplate.java       # 模板 VO
│   ├── HandwritingExportVO.java       # 导出响应 VO
│   └── HandwritingImportVO.java       # 导入响应 VO
└── config/
    └── HandwritingRenderConfig.java   # 渲染配置
```

### 后端修改文件

```
CardRenderPipeline.java     # +renderWithJs(), +renderToPdf(), 细粒度路由
CardTemplateEngine.java     # SUPPORTED_STYLES → CardStyle 枚举
CardController.java         # resolveCardStyle() → CardStyle 枚举
CardService.java            # DELETE 条件增加 style, style=handwriting 走 HandwritingRenderer
GlobalExceptionHandler.java # 手写相关异常处理
application.yml             # handwriting 配置段
```

### 前端新增文件

```
frontend/src/
├── pages/handwriting/
│   ├── HandwritingEditorPage.vue      # 独立编辑器页面
│   └── components/
│       ├── FontSelector.vue           # 字体选择器
│       ├── PaperSelector.vue          # 纸张选择器
│       ├── ParamSliders.vue           # 参数调节滑块
│       ├── TemplatePicker.vue         # 模板选择器
│       ├── TextEditor.vue             # 文字编辑区
│       └── PreviewPanel.vue           # 预览面板
├── api/handwritingController.ts       # API 调用
├── stores/handwritingStore.ts         # Pinia 状态
└── router/                            # + handwriting 路由
```

### 资源文件

```
resources/
├── handwriting/
│   ├── fonts/              # 字体文件 (.ttf/.otf)
│   │   └── LICENSE.txt     # 字体许可证汇总
│   └── templates/          # 预设模板 JSON
│       ├── homework.json
│       ├── class-note.json
│       ├── essay.json
│       └── jottings.json
└── templates/cards/
    └── handwriting.html    # Card 风格的 handwriting 模板（简版）
```

---

## 六、测试计划

### 单元测试

| 测试类 | 覆盖 |
|--------|------|
| `HandwritingRendererTest` | sanitizeContent XSS 清洗、空内容/NPE、生僻字检测回归 |
| `HandwritingPaperServiceTest` | 5 种纸张 CSS 输出正确性 |
| `HandwritingStructurePlannerTest` | 分页逻辑、边界（单页/空内容/超长）、段落边界维护 |
| `HandwritingFontManagerTest` | 字体列表、URL 生成、缺失字体处理 |
| `CardStyleTest` | 枚举解析、未知值回退 |
| `CardTemplateEngineTest` | handwriting 风格白名检验证 |

### 集成测试

| 测试 | 验证点 |
|------|--------|
| `HandwritingControllerIntegrationTest` | 全部 API 端点响应、参数校验、频率限制、配额扣减/退还 |
| `CardRenderPipelineHandwritingTest` | JS 启用渲染、COS URL 白名单路由、字体加载、PDF 导出 |
| `CardServiceHandwritingTest` | style=handwriting 走新分页/渲染器、幂等删除不误删其他风格 |

### 端到端测试（前端 Playwright）

| 测试 | 验证点 |
|------|--------|
| 手写编辑器页面加载 | 字体/纸张/模板列表渲染 |
| 预览按钮 | 输入文字 → 预览 → 图片出现 |
| 导出 PNG/PDF | 点击导出 → 下载触发 |
| Card 风格 handwriting | 文章完成 → 选择 handwriting 风格 → 卡片生成成功 |
| SSE 生命周期 | 预览/导出前后连接正确关闭 |

---

## 七、暗坑总结（审计沉淀）

> 每次修改相关代码前先读此清单

| # | 坑 | 部位 | 正确做法 |
|---|-----|------|---------|
| 1 | Playwright JS 禁用 | CardRenderPipeline | 新方法 `renderWithJs()` 启用 JS，不碰原方法 |
| 2 | 全局 route.abort() 阻断字体 | CardRenderPipeline | COS 域名白名单路由 |
| 3 | 幂等删除缺 style 条件 | CardService | DELETE WHERE task_id AND style |
| 4 | th:utext XSS | handwriting.html | Java 侧 Jsoup.clean() 清洗后再注入 |
| 5 | 中文字体 OOM | CardRenderPipeline | BrowserContext 级别复用，不每页 new |
| 6 | 字体许可证 | handwriting/fonts/ | 仅用 SIL OFL，归档 LICENSE |
| 7 | 预览端点无频控 | HandwritingController | @RateLimit(10/min) |
| 8 | SSE key 冲突 | SseEmitterManager | handwriting_ 前缀隔离 |
| 9 | COS key 隔离 | HandwritingService | handwriting/{taskId}/ 前缀 |
| 10 | 生僻字回退 | HandwritingRenderer | 检测→warning + system font fallback |
