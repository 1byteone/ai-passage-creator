# 插画人物卡片风格子系统 — 设计文档

> **日期**: 2026-08-05
> **分支**: dev
> **状态**: 已批准（头脑风暴对齐 8 项决策 + anysearch 调研 6 个素材库）
> **前置依赖**: 图文卡片子系统已完成（CardStyle / CardTemplateEngine / CardRenderPipeline）

---

## 1. 背景与目标

现有图文卡片支持 warm / minimal / free / handwriting 四种风格，均为纯排版风格（无人物形象）。目标：新增 **插画人物风格（illustration）**，在卡片中呈现 AI 动态生成的插画人物形象，提升小红书/抖音内容的情感温度与辨识度。

### 1.1 参考来源

- **anysearch 调研**: Canva 小红书爆款风向标（插画风 15 类）、稿定 AI 提示词系统（母版+变量）、ComfyUI 封面工作流（主体突出+标题区留白）
- **IP 设计三原则**（ip-design.cn）: 视觉识别 / 故事叙事 / 商业变现
- **素材库**: unDraw / Storyset / 爱给网 / Open Peeps / Pixabay（许可证已核实）

### 1.2 核心能力

1. 新增 `illustration` 卡片风格（`CardStyle` 枚举白名单）
2. AI 根据文章标题/主题动态生成匹配的插画人物（Agnes 生图，4 种子风格）
3. 封面主视觉大图（IP 形象）+ 内页人物角标/小插画装饰
4. AI 生成失败 → 静态素材库熔断（classpath 内嵌，不中断卡片）
5. 前端双入口：CardPage 风格切换 + 创作链路内嵌默认

---

## 2. 范围

### 2.1 In-scope（本次交付）

1. `CardStyle` 枚举 + `ILLUSTRATION("illustration")`
2. `IllustrationImageService` — 生图编排（AI 主力 → 熔断）
3. `IllustrationPromptBuilder` — 提示词母版 + 主题映射
4. `IllustrationCharacterStyle` — 子风格枚举（healing/cute/doodle/watercolor）
5. `StaticIllustrationLibrary` — 静态素材库（classpath 内嵌 + 授权记录）
6. `illustration.html` — 封面大图 + 内页角标模板
7. `CardController/CardService/CardAsyncService/CardTemplateEngine` 改造（透传 characterStyle）
8. `CardGenerateRequest` + `characterStyle` 字段（@Valid 白名单校验）
9. 静态素材入库（4 子风格 × 2-3 素材 + LICENSES.md）
10. 单元测试 + 集成测试

### 2.2 Out-of-scope（后续阶段）

- 用户上传照片转插画（未来拓展，接口预留）
- 插画人物的动效（JS 渲染，走 renderWithJs）
- 插画人物的用户自定义编辑/重排
- 插画人物与前端全局 UI 主题联动

---

## 3. 需求对齐（头脑风暴沉淀）

| 维度 | 决策 |
|------|------|
| 主题范围 | 新增 `illustration` 卡片风格（复用卡片管线） |
| 人物来源 | ① AI 动态生成为主力 ② 静态素材库熔断 ③ 用户上传转插画=未来拓展 |
| 风格方向 | 治愈系暖调为主，手绘涂鸦/Q版扁平/国风水彩为可选子风格 |
| 布局角色 | 封面主视觉大图（IP 形象）+ 内页人物角标/小插画装饰 |
| 人物个性化 | 随文章主题动态生成匹配的插画人物 |
| 前端入口 | CardPage 风格切换 + 创作链路内嵌默认，两者都要 |
| 配额策略 | 扣 AI 图像配额，每次封面生成扣 1，失败退还 |
| 熔断行为 | AI 失败自动静默回退到静态素材 |

---

## 4. 技术路径（方案 A：模板注入）

**决策**: 静态插画 + 复用现有安全渲染管线（JS disabled + route abort + `th:text` 转义）。

**理由**:
- 插画人物本质是静态图，无需动效 → 不需要 `renderWithJs()` 启用 JS
- 复用 `CardTemplateEngine.render()` 现有安全管线，改动面最小
- 人物 URL 仅来自 COS 预签名 URL / 静态素材 URL，无用户输入注入
- 符合 "Simplicity First" / "Security By Default"

**Trust Spectrum**: 🟡 业务逻辑 + 🟢 样板代码，测试覆盖充分。

---

## 5. 总体架构

```
POST /article/cards/generate  { cardStyle: "illustration", characterStyle: "healing" }
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│ CardController (resolveCardStyle 扩展 + characterStyle 校验)  │
└──────────────────────────────┬───────────────────────────────┘
                               ▼
┌──────────────────────────────────────────────────────────────┐
│ IllustrationImageService (新增 @Service)                     │
│  1. generateCoverImage(mainTitle, characterStyle, taskId)    │
│     ├─ IllustrationPromptBuilder.build() 提示词母版组装       │
│     ├─ AI 主力: AgnesImageService.searchImage(prompt)         │
│     │    → 成功 → CosService.uploadToKey → 预签名 URL         │
│     └─ 熔断: 失败 → StaticIllustrationLibrary 静态素材 URL    │
└──────────────────────────────┬───────────────────────────────┘
                               ▼
┌──────────────────────────────────────────────────────────────┐
│ CardTemplateEngine.render(pages, "illustration", 插画URL)    │
│   └─ templates/cards/illustration.html                       │
│       ├─ COVER: 封面人物大图 + 标题叠加                       │
│       └─ CONTENT: 文字卡片 + 内页人物角标/小插画              │
│   └─ 走现有 render() 安全管线 (JS disabled + route abort)    │
└──────────────────────────────┬───────────────────────────────┘
                               ▼
        CardRenderPipeline.render → Playwright 截图 → COS → 持久化
```

### 组件清单

| 组件 | 类型 | 职责 | 依赖 |
|---|---|---|---|
| `IllustrationImageService` | @Service 新增 | 编排插画人物生成（AI主力→熔断） | AgnesImageService, CosService, StaticIllustrationLibrary |
| `IllustrationPromptBuilder` | @Component 新增 | 提示词母版组装（含 characterStyle 子风格） | 无 |
| `IllustrationCharacterStyle` | 枚举 新增 | 子风格白名单: healing/cute/doodle/watercolor | — |
| `StaticIllustrationLibrary` | @Component 新增 | 预置插画素材（按子风格组织） | classpath 资源 |
| `illustration.html` | 模板 新增 | 封面大图 + 内页角标布局 | Thymeleaf |
| `CardStyle` | 枚举 修改 | + ILLUSTRATION("illustration") | — |
| `CardController` / `CardService` / `CardTemplateEngine` / `CardAsyncService` | 修改 | 透传 characterStyle + illustration 分支 | — |

---

## 6. 模板设计（illustration.html）

### 6.1 结构

```
COVER 页 (pageType=COVER)：
  插画人物大图 (主视觉) — 占画面约 65%，居中
  主标题叠加 (下方, 白字+暖色描边)
  副标题 (小字)

CONTENT 页 (pageType=CONTENT)：
  插画人物角标 (右上角, ~120px 小图)
  章节标题
  正文内容
  插画小元素点缀 (底部, 可选)
```

### 6.2 模板上下文变量扩展

`CardTemplateEngine.render()` 现有注入 `title/content/pageNo`，插画模板额外注入：

```java
ctx.setVariable("coverImageUrl", illustrationUrl);      // 封面人物大图
ctx.setVariable("characterStyle", characterStyle);       // healing/cute/doodle/watercolor
ctx.setVariable("characterIconUrl", characterIconUrl);   // 内页角标小图
ctx.setVariable("pageType", page.getPageType());         // COVER / CONTENT
```

### 6.3 子风格色板

| 子风格 key | 中文名 | 背景 | 文字 | 强调色 |
|---|---|---|---|---|
| `healing` | 治愈系暖调（默认） | 暖米黄 `#F5E6D3` | 深棕 `#2D1810` | 焦糖橙 `#D4956A` |
| `cute` | Q版可爱扁平 | 奶油白 `#FFF8F0` | 深灰 `#2D2D2D` | 珊瑚粉 `#E07B6B` |
| `doodle` | 手绘涂鸦 | 米白纸纹 `#FFF8F0` | 深棕黑 `#2D1F14` | 薄荷绿 `#7BB89A` |
| `watercolor` | 国风水彩 | 浅米白纸纹 `#F5F1E8` | 墨黑 `#1A1A1A` | 朱红 `#D14545` |

### 6.4 安全

- `th:text` 转义，不用 `th:utext`
- 静态图 → 现有 render() 管线（JS disabled + route abort）

---

## 7. 生图管线与提示词母版

### 7.1 流程

```
IllustrationImageService.generateCoverImage(mainTitle, characterStyle, taskId)
  ├─ 1. IllustrationPromptBuilder.build(mainTitle, characterStyle) → 提示词
  ├─ 2. AI 主力: AgnesImageService.searchImage(prompt) → URL
  │     ├─ 成功 → CosService.uploadToKey(illustration/{taskId}/cover.png) → 预签名 URL
  │     └─ 失败 → 熔断
  └─ 3. 熔断: StaticIllustrationLibrary.get(characterStyle) → classpath 素材 URL
```

### 7.2 提示词母版

```
[子风格模板] 竖版卡片封面插画人物，[人物形象描述]，
[子风格视觉特征]，[配色]，主体突出，背景干净留白，
预留标题区（下方 30% 区域留白放标题），无文字，
竖版 9:16，治愈系插画，高清细腻
```

**子风格模板片段**：

| 子风格 | 人物形象 | 视觉特征 | 配色 |
|---|---|---|---|
| healing | 温柔微笑的插画女性/人物 | 柔和线条、温暖光晕 | 暖米黄+焦糖橙+奶油白+抹茶绿 |
| cute | Q版可爱卡通人物，夸张比例 | 圆润几何、柔和色块 | 奶油白+珊瑚粉+薄荷绿 |
| doodle | 手绘涂鸦人物 | 马克笔笔触、粗黑描边 | 米白纸纹+深棕+薄荷绿 |
| watercolor | 水墨人物 | 毛笔线条、淡彩晕染 | 浅米纸纹+朱红+墨黑 |

**主题映射**: mainTitle 智能映射 → 人物身份/动作（知识→书桌学者、养生→品茶女性、科技→职场青年），内置关键词表 + 兜底默认。

### 7.3 内页角标

- 复用封面人物图，模板 CSS `object-fit: cover` + 固定尺寸容器缩放
- 零额外 AI 调用（省配额）

### 7.4 安全边界

| 点 | 处理 |
|---|---|
| Prompt 注入 | mainTitle 只取纯文本，白名单主题映射，不拼接用户自由文本进提示词 |
| URL 信任 | 人物 URL 仅来自 AI 返回值（走 COS）或静态库，无用户输入 |
| XSS | 模板 `th:text` 转义，不用 `th:utext` |
| 渲染安全 | 静态图 → 现有 render() 管线（JS disabled + route abort） |

### 7.5 配额 & 熔断

| 场景 | 行为 |
|---|---|
| AI 生图成功 | 扣 1 图像配额（现有 quotaService） |
| AI 生图失败 | 熔断 → 静态素材 URL（不扣配额），卡片照常生成 |
| 静态素材也缺失 | 抛业务异常「插画素材缺失」，走现有卡片失败流程 |

---

## 8. 静态素材库（熔断层）

### 8.1 素材源（anysearch 调研）

| 库 | 风格 | 商用 | 署名 | 获取方式 | 说明 |
|---|---|---|---|---|---|
| **unDraw** | 现代扁平插画 | ✅ | ❌ 免署名 | API/SVG 直链/GitHub | 可商用免署名，禁止 AI 训练/批量分发（熔断渲染合规） |
| **Storyset** | 场景插画 | ✅ | ❌ | 在线下载 | unDraw 同系 |
| **爱给网 aigei** | 国内免费矢量（含人物） | ✅ | 视素材 | 网页下载 | 国内直连，CC0/可商用筛选 |
| **Open Peeps** | 手绘人物插画 | ✅ | ✅ 需署名 | 网页下载 | 对应手绘涂鸦风 |
| **Pixabay** | 全品类插画 | ✅ | ❌ | 网页下载 | 中文站 |

### 8.2 落地策略

`StaticIllustrationLibrary` 采用 **classpath 内嵌为主**：
1. 从 unDraw（SVG 可着色）下载 4 子风格 × 2-3 个人物素材，转 PNG 存入 `resources/illustration/{style}/`
2. AI 生成失败 → 按 `characterStyle` 从 classpath 取对应素材 URL
3. 素材授权类型记录在 `resources/illustration/LICENSES.md`（含来源 + 授权协议）

### 8.3 授权风险提示

- unDraw/Storyset：禁止 AI 训练/批量转售 → 仅作卡片熔断渲染完全合规
- 爱给网：逐素材确认 CC0/CC 署名协议，`StaticIllustrationLibrary` 记录每张素材授权类型
- Icons8/Ouch：免费版需署名且限流量，**不建议**作为熔断层

---

## 9. 后端改动清单

### 9.1 新增文件

```
src/main/java/com/example/aipassagecreator/card/
├── illustration/
│   ├── IllustrationImageService.java     # 生图编排（AI主力→熔断）
│   ├── IllustrationPromptBuilder.java    # 提示词母版 + 主题映射
│   ├── IllustrationCharacterStyle.java   # 枚举: healing/cute/doodle/watercolor
│   └── StaticIllustrationLibrary.java    # 静态素材库（classpath 内嵌）
├── model/
│   └── IllustrationGenerateRequest.java  # 生成请求 DTO
```

### 9.2 修改文件

| 文件 | 改动 |
|---|---|
| `CardStyle.java` | + `ILLUSTRATION("illustration")` |
| `CardTemplateEngine.java` | `render()` 增加 illustration 分支：注入插画变量 + 走 `illustration.html` |
| `CardController.java` | `resolveCardStyle()` 已通用；增加 `characterStyle` 参数透传 |
| `CardService.java` | `generate()`/`preview()` 增加 `characterStyle` 参数，style=illustration 时调用 `IllustrationImageService` |
| `CardAsyncService.java` | `generateCards()` 透传 `characterStyle` |
| `CardGenerateRequest.java` | + `characterStyle` 字段（@Valid 校验枚举白名单） |
| `illustration.html` | 新建模板（封面大图 + 内页角标） |
| `resources/illustration/` | 静态素材 + LICENSES.md |

### 9.3 请求参数透传链

```
前端 cardStyle="illustration" + characterStyle="healing"
  → CardGenerateRequest.cardStyle + characterStyle
  → CardController（resolveCardStyle + characterStyle 白名单校验）
  → CardAsyncService.generateCards(taskId, cardStyle, characterStyle, ...)
  → CardService.generate(..., cardStyle, characterStyle, ...)
      ├─ style=illustration → IllustrationImageService.generateCoverImage()
      ├─ templateEngine.render(pages, "illustration", charUrl, charStyle, charIconUrl)
      └─ 其余分支不变
```

### 9.4 配额接入

复用现有 `quotaService.checkAndConsumeQuota()`（CardController 已调用）：
- AI 生图成功 → 扣 1（现有逻辑，无需新增）
- AI 生图失败熔断 → 静态素材不扣配额（`IllustrationImageService` 返回标记）
- 预览：不扣（与现有 preview 一致）

---

## 10. 前端改动

### 10.1 CardPage 风格切换

- 卡片生成区域增加「风格」下拉（含「插画人物」选项）
- 选中插画人物时，显示「子风格」二级选择（治愈系/Q版/手绘/国风）
- 调 `generateCards({ cardStyle: "illustration", characterStyle: "healing" })`

### 10.2 创作链路内嵌默认

- 文章完成态（CompletedState）增加插画风格记忆（sessionStorage）
- 进入 CardPage 时自动携带已选风格

> 注：前端具体 UI 细节将在实施阶段细化（本设计聚焦后端数据流与模板）。

---

## 11. 错误处理

| 场景 | 策略 |
|---|---|
| AI 生图失败 | 熔断 → 静态素材，卡片照常生成 |
| 静态素材缺失 | 抛业务异常「插画素材缺失」，走现有卡片失败流程 |
| characterStyle 非法 | @Valid 校验拒绝，`PARAMS_ERROR` |
| 配额不足 | 明确报错，不扣减（复用现有） |

---

## 12. 测试策略

| 层级 | 测试内容 | 方式 |
|---|---|---|
| 单元测试 | `IllustrationPromptBuilderTest`（子风格模板组装/主题映射/防注入） | JUnit 5 |
| | `IllustrationImageServiceTest`（AI 成功/失败/熔断 三分支，mock Agnes） | JUnit 5 |
| | `StaticIllustrationLibraryTest`（素材存在性/风格映射/授权记录） | JUnit 5 |
| | `CardStyleTest`（扩展）illustration 白名单 + 回退 | JUnit 5 |
| 集成测试 | `CardServiceIllustrationTest`（style=illustration 编排、幂等删除不误删其他风格） | @SpringBootTest + H2 |
| | `CardControllerIllustrationTest`（characterStyle 校验、配额扣减/退还） | @SpringBootTest + H2 |
| 安全回归 | 端点 @AuthCheck / 归属校验 / 状态门禁 / 配额 | 新增安全测试 |

**验收标准**: 现有测试全部通过；新增测试覆盖上述场景。

---

## 13. 目录结构

```
新增:
  src/main/java/com/example/aipassagecreator/card/
    ├── illustration/
    │   ├── IllustrationImageService.java
    │   ├── IllustrationPromptBuilder.java
    │   ├── IllustrationCharacterStyle.java
    │   └── StaticIllustrationLibrary.java
    └── model/IllustrationGenerateRequest.java
  src/main/resources/
    ├── templates/cards/illustration.html
    └── illustration/
        ├── healing/{2-3 素材}.png
        ├── cute/{2-3 素材}.png
        ├── doodle/{2-3 素材}.png
        ├── watercolor/{2-3 素材}.png
        └── LICENSES.md

修改:
  CardStyle.java                 # + ILLUSTRATION
  CardTemplateEngine.java        # illustration 分支
  CardController.java            # characterStyle 透传
  CardService.java               # characterStyle 参数 + illustration 分支
  CardAsyncService.java          # characterStyle 透传
  model/CardGenerateRequest.java # + characterStyle 字段
```

---

## 14. 风险与缓解

| 风险 | 缓解 |
|---|---|
| AI 生图不稳定 | 熔断静态库兜底，卡片不中断 |
| 静态素材授权纠纷 | 仅用核实过的免费商用库 + LICENSES.md 记录授权类型 |
| unDraw 禁止 AI 训练 | 仅作卡片熔断渲染，不用于训练/转售，完全合规 |
| 人物形象与内容不匹配 | 主题映射表 + 兜底默认形象 |
| characterStyle 穿透 | @Valid 枚举白名单校验 |
| 渲染安全 | 复用现有 render() 管线（JS disabled + route abort） |
