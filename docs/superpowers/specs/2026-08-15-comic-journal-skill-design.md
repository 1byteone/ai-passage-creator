# 漫画手帐 Skill（comic-journal）设计文档

> 日期：2026-08-15
> 状态：已批准
> 目标仓库：https://github.com/acheAIsuiyimen/ache-life-to-comic-skill （仅借鉴设计思路，不拷贝代码/资产）

## 1. 背景与目标

将「生活转漫画手帐」能力以**平台内新 Skill** 的形式接入 ai-passage-creator：用户把日常、心情、照片、知识笔记、会议纪要、长文任意混合输入，系统自动判断内容类型、拆解分镜/版位、按画风生成配图，产出**按月成册的漫画手帐**（HTML 主版本 + PNG 导出）。

**对齐的关键需求**（头脑风暴已确认）：
| 维度 | 决策 |
|---|---|
| 接入方向 | 平台内新 Skill（skill.yaml 编排 + 后端服务消费渲染落库） |
| 输出形态 | HTML 主版本（可预览/成册） + PNG 导出（复用 Playwright 管线） |
| 输入范围 | 全类型路由：daily / photo / knowledge / meeting / longform |
| 成书系统 | 完整成书：章（episode）→ 月册（monthly volume）→ 部/年册（查询聚合索引） |
| 合规策略 | 完全自行实现，不拷贝目标仓库脚本/CSS/资产（规避 PolyForm Noncommercial） |
| 画风 | 4 款自建画风预设，提示词 + CSS 双写基线 |
| 数据存储 | MySQL 业务表（comic_book / comic_episode / comic_monthly_volume） |
| 照片处理 | 支持上传（COS 存 URL），基础排版（原图保留 + 外框/旁注变化） |

## 2. 架构总览

```
用户 SkillExecutePage(comic-journal) 输入文本/照片
  → POST /skill/comic-journal/execute
  → StateGraph 4 阶段（agnes 主 / dashscope 降级，SSE 进度）
     ├─ route_content    LLM 判断类型 + 提炼真实节拍
     ├─ storyboard       分镜节拍 JSON / 照片版位 photoSlots（★ HITL 确认）
     ├─ illustration     每格生图提示词（画风注入）
     └─ compose          排版 JSON（封面/文字块/图位/留白）
  → ComicJournalService（skill SUCCESS 后异步消费输出）
     ├─ 生图循环 AgnesImageService（画风提示词, 失败降级静态占位）
     ├─ ComicTemplateEngine → 章节 HTML（Thymeleaf + 画风 CSS）
     ├─ ComicRenderPipeline → PNG（可选导出）
     ├─ 落库 comic_episode + 月册聚合 comic_monthly_volume
     └─ SSE 完成事件
  → 前端预览 HTML / 下载 PNG / /comic 手帐浏览页
```

**关键架构决策**：skill 引擎只做 LLM 编排，输出结构化 JSON；渲染/生图/落库由专用完成处理器（ComicJournalService）在 skill 成功后异步消费——与现有 `RAG indexSkillAsync` 同模式，不改动 StateGraph 核心。

## 3. Skill 编排

### 3.1 变量定义

| 变量 | uiType | 必填 | 说明 |
|---|---|---|---|
| `content` | textarea | 是（photo 场景可只填描述） | 日常/心情/知识/会议/长文文本 |
| `photos` | upload（新增） | 否 | 照片数组（0-6 张，COS URL） |
| `style` | select | 否 | 画风：`powder`(默认)/`gouache`/`colorpencil`/`inkwash` |
| `bookName` | input | 否 | 手帐档案名，默认「我的生活手帐」 |

### 3.2 阶段定义（输出 JSON 契约）

```
phase1 route_content
  输出 { type: daily|photo|knowledge|meeting|longform,
         title, beats[], tone, summary }
  行为：判断内容类型 + 提炼真实节拍/要点，不硬塞剧情

phase2 storyboard （requireConfirmation: true — 分镜 HITL）
  输出 { panels[]: { panelNo, shot, composition, content, emotion, captionText } }
  photo 类型输出 { photoSlots[]: { slotNo, photoIndex, frame, note } }
  行为：daily/meeting/knowledge/longform 走分镜格；photo 保留原图规划版位

phase3 illustration
  输出 { imagePrompts[]: { panelNo, prompt } }
  行为：画风提示词约束注入（媒介/配色/构图），角色锁定，禁止搬运参考图具体人物/Logo

phase4 compose
  输出 { page: { cover: {title, subtitle, tone}, sections[],
         textBlocks[]: {blockNo, content, style}, imagePlacements[]: {panelNo, imageUrl, frame} } }
  行为：排版 JSON；中文标题平衡换行，不留单字孤行；文字为主时图少而有用
```

### 3.3 编排关键点

- **分镜 HITL**：phase2 后中断等待用户确认（approve/modify/retry，复用现有 `POST /skill/{executionId}/confirm`），用户可改分镜或换画风再续跑——避免浪费生图。
- **画风基线双写**：`ComicStyle` 枚举持有「提示词约束 + CSS 变量」两套基线；illustration 阶段注入提示词约束，渲染阶段注入 CSS。
- **照片类型**：photo 输入时 storyboard 输出 `photoSlots` 而非 `panels`，渲染时原图保留 + 外框/旁注基础排版。
- 模型：各阶段 `model: agnes`，`ModelRouter` 自动降级 dashscope。

## 4. 数据模型

3 张新表（逻辑删除 `is_delete` + `create_time/update_time` 通用列；Flyway `V{n}` 迁移 + 同步 h2-schema.sql）。

### 4.1 comic_book（手帐档案）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | |
| user_id | BIGINT NOT NULL | 属主 |
| book_name | VARCHAR(64) | 档案名 |
| default_style | VARCHAR(32) | 默认画风 |

### 4.2 comic_episode（章节，每次记录一章）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | |
| book_id | BIGINT | → comic_book.id |
| episode_no | INT | 章序号（book 内自增） |
| title | VARCHAR(128) | 章节标题 |
| input_type | VARCHAR(16) | daily/photo/knowledge/meeting/longform |
| input_summary | VARCHAR(512) | 内容摘要 |
| style | VARCHAR(32) | 本次画风 |
| route_result | LONGTEXT | route 阶段输出（JSON 串） |
| storyboard_result | LONGTEXT | 分镜/版位 JSON |
| image_prompts | LONGTEXT | 生图提示词 JSON |
| layout_result | LONGTEXT | 排版 JSON |
| page_html | LONGTEXT | 章节 HTML 产物（预览用） |
| png_url | VARCHAR(512) | PNG 导出地址 |

### 4.3 comic_monthly_volume（月册索引）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | |
| book_id | BIGINT | → comic_book.id |
| year_month | CHAR(7) | '2026-08'（book 内唯一） |
| episode_count | INT | 当月章节数 |
| index_html | LONGTEXT | 当月连续阅读页 HTML |
| cover_title | VARCHAR(128) | 月册标题 |

### 4.4 成书层级

- 章 = comic_episode（每次记录）
- 月册 = comic_monthly_volume（聚合当月章节 → 重写 index_html）
- 部/年册 = 查询聚合（不建表）：按 `year_month` 分组季度/年度索引

**JSON 存储**：route/storyboard/illustration/layout 输出一律 LONGTEXT（JSON 字符串），不建 MySQL JSON 列——规避 H2 测试档 json 兼容问题。

## 5. 渲染体系（Java 侧自建）

### 5.1 ComicStyle 枚举（4 款画风双写基线）

| 画风 | 提示词约束 | CSS 变量要点 |
|---|---|---|
| `powder` 粉蜡（默认） | 雪白底/冰蓝炭灰/粉蜡笔触/大留白 | 纯白背景、炭灰主色、冰蓝点缀 |
| `gouache` 水粉 | 云层水粉/空气感/松软晕染 | 奶油纸、低饱和粉彩、柔和阴影 |
| `colorpencil` 彩铅 | 白纸彩铅/清晰线稿/认真手记 | 白底、彩铅线条、明快色点 |
| `inkwash` 细墨 | 细墨轻彩/稳健线稿/阅读向 | 宣纸白、墨色、朱红点睛 |

### 5.2 ComicTemplateEngine（Thymeleaf）

- `templates/comic/{style}/episode.html` — 单章页：封面 + 分镜格 + 图位 + 文字块 + 旁注 + 留白
- `templates/comic/{style}/monthly.html` — 月册连续阅读页
- 全部 `th:text` 自动转义防 XSS；注入：title / panels / imageUrls（base64 内联）/ textBlocks / styleCss

### 5.3 ComicRenderService（复用 CardRenderPipeline）

- 章节 HTML → PNG：走标准 `render` 安全模式（JS 禁用 + 布局探针 + 拒绝外部网络）
- 图片内联：生图/照片 URL → `CardImageResolver.toDataUrl` base64（避免禁网下远程图加载失败）；照片场景可选 COS 白名单 `renderWithJs` 模式
- Playwright 不可用时跳过 PNG，仅保留 HTML（优雅降级）

### 5.4 ComicJournalService（skill 完成后异步消费）

```
读取 skill outputData（route/storyboard/illustration/layout）
  → 生图循环（AgnesImageService + 画风提示词，1-3 格，失败降级静态占位）
  → ComicTemplateEngine 渲染章节 HTML
  → ComicRenderPipeline 出 PNG（可选导出）
  → 落库 comic_episode + 月册聚合（upsert index_html）+ 更新书
  → SSE 完成事件（附 episodeId/pngUrl）
```

**质量底线**：纯白背景；图框比例容差；中文标题无单字孤行；照片原图完整；文字为主时图少而有用。

## 6. 前端

1. **SkillCenterPage**：新增「漫画手帐」技能卡片（category: image）。
2. **后端通用上传端点** `POST /file/upload`：MultipartFile → `CosService.uploadBytes` → URL；校验图片类型（jpg/png/webp）+ 大小（≤10MB）。
3. **SkillInputForm**：新增 `uiType: 'upload'` 分支（a-upload 多图 0-6 张，before-upload → /file/upload → 存 URL 数组到 photos）。
4. **结果渲染**：SkillResultRenderer 新增 comic-journal 分支——iframe srcdoc 预览章节 HTML（不引入 v-html）+ 下载 PNG + 打开手帐库。
5. **手帐浏览页** `/comic`（需登录，nav 加入口）：档案 → 月册 → 章节；iframe 预览 + 下载 PNG；部/年册按季度/年度分组展示。
6. **SSE 进度文案**：分镜生成中 / 生图中 / 排版中 / 渲染中。

## 7. 错误处理与测试

### 7.1 错误处理

| 场景 | 处理 |
|---|---|
| LLM 调用失败 | ModelRouter 降级（agnes→dashscope）+ 现有阶段熔断重试 |
| 生图失败 | 降级静态占位图，不中断流程 |
| Playwright 不可用 | 跳过 PNG，仅保留 HTML |
| 文件上传非法 | 后端校验类型/大小，返回中文错误 |
| HITL 分镜确认超时 | 复用 SkillConfirmationReaper 超时收割 |
| 月册聚合 | book_id + year_month 唯一 upsert |

### 7.2 测试

- 后端单测：`ComicStyleTest`、`ComicJournalServiceTest`、`ComicTemplateEngineTest`（模板无 XSS 注入）
- 后端集成：`ComicSkillIntegrationTest`（H2：skill 执行 → 渲染 → comic_episode/monthly_volume 落库断言）
- 前端：type-check 零错误 + `npm run build`；upload 控件单测
- 命名沿用 `methodName_scenario_expectedResult()`

## 8. 范围与非目标

**范围**：comic-journal skill（4 阶段编排 + HITL）、3 张表、4 款画风、/file/upload 端点、/comic 浏览页、PNG 导出、照片基础排版。

**非目标（后续迭代）**：
- 参考图拆解（上传参考图 → 拆媒介/配色/构图注入画风）
- 照片高级排版（异形纸托/手撕纸/异形窗口）
- 分享导出单文件（share-exports 保真版）
- 角色/IP 系统（持续人物主导镜头）
- 并发多用户书级锁（当前单人书记录冲突低）

## 9. 合规说明

目标仓库采用 PolyForm Noncommercial 1.0.0 许可证。本设计**不拷贝**其脚本（scripts/*.mjs）、CSS、design-baseline.json、画风样张、角色资产；仅借鉴「内容路由 + 画风预设 + 月册成书」的产品设计思路，全部实现基于当前项目自有能力（Java + Thymeleaf + Playwright + Agnes 生图）。
