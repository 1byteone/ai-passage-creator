# 图文卡片生成子系统 设计文档（第二阶段）

- **日期**：2026-08-01
- **分支**：dev
- **状态**：已批准（含三轮专业审计修订吸收）
- **前置依赖**：第一阶段「爆款方法论引擎」已完成（MethodologyRegistry / 创作引导 / 爆款评测 / 反哺闭环）

---

## 1. 背景与目标

第一阶段已完成「爆款方法论引擎」，使文章创作具备方法论引导与爆款评测。第二阶段目标是**将文章渲染为多风格竖版轮播卡片**，并填充小红书/抖音方法论模板，使内容可跨平台发布。

### 1.1 参考来源

- **qianjiazheng2023/xiaohongshu-article-cards**：小红书图文卡片创作 Skill，多风格模板、前两页预览确认、合规自动检查
- **RyanYipeng/SyncCaster**：PlatformAdapter 适配器模式、统一内容模型

### 1.2 核心能力

1. 文章正文 → 智能分页（封面页 + 内容页）
2. 多风格 Thymeleaf HTML 模板渲染（奶油信息卡/极简蓝白/自由风格）
3. Playwright 安全渲染 HTML → 竖版 PNG（1080×1920 @2x）
4. 合规检查（文本规则 + 布局探针双重门禁）
5. COS 私有桶 + 预签名 URL 存储
6. 前两页预览确认 → 全量异步生成

---

## 2. 范围

### 2.1 In-scope（第二阶段交付）

1. 方法论模板扩展：`PlatformConfig.cardStyle` 字段 + `mergePlatform` 合并 + `default.yaml` platform 段
2. 填充 `xiaohongshu.yaml` / `douyin.yaml` 完整方法论（含平台规则/创作维度/评测维度）
3. 卡心智分页引擎 `CardStructurePlanner`（按章节 + 500 字上限分页）
4. 多风格 HTML 模板（Thymeleaf：warm / minimal / free）
5. 安全渲染管线 `CardRenderPipeline`（Playwright + CSP + 布局探针 + 截图）
6. 合规检查器 `CardComplianceChecker`（文本规则 + 布局探针双重门禁）
7. 卡片持久化 `article_card` 表 + 幂等 upsert
8. 异步生成管线 `CardAsyncService`（复用 SSE 进度推送）
9. REST 端点（preview / generate / 查询）+ 安全闸门
10. 配额接入 + 单用户日上限
11. 依赖补充（pom.xml Thymeleaf / Playwright / flexmark）
12. 单元测试 + 集成测试（H2）

### 2.2 Out-of-scope（后续阶段）

- 多平台分发（Phase 3，仅做接口预留）
- 合规检查规则的自定义/后台管理
- 卡片的动态编辑/重排
- 卡片批量导出/下载
- 分析统计（卡片点击率、分享率）

---

## 3. 架构

### 3.1 总体架构

```
POST /article/cards/preview  (同步, 2页)
POST /article/cards/generate (异步, 返回 taskId)
GET  /article/cards/{taskId} (查询)

┌─────────────────────────────────────────────────────────────────────┐
│ CardController (@AuthCheck + @RateLimit + 归属校验 + 配额)          │
└──────────────────────────┬──────────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ CardService (编排)                                                  │
│  1. CardStructurePlanner.plan(fullContent, style) → List<PagePlan>  │
│  2. CardComplianceChecker.textCheck(plan) → pre-report              │
│  3. CardRenderPipeline.render(plan) → List<PageResult>              │
│     ├─ Playwright 加载 HTML (CSP: default-src 'none', JS disabled)  │
│     ├─ DOM 布局探针(溢出/裁切/重叠) → 失败则跳过截图               │
│     └─ 合格 → screenshot() → PNG bytes                              │
│  4. CosService.uploadBytes(png, "image/png", "cards/{taskId}")      │
│  5. CardPageMapper 持久化                                           │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 组件清单

| 组件 | 类型 | 职责 | 依赖 |
|---|---|---|---|
| `CardService` | @Service | 编排完整管线 | 全部下级组件 |
| `CardStructurePlanner` | @Component | 文章→分页结构（章节/500字上限/封面页） | flexmark（Markdown AST） |
| `CardTemplateEngine` | @Component | Thymeleaf 程序化渲染 HTML | `spring-boot-starter-thymeleaf` |
| `CardRenderPipeline` | @Component | Playwright 池化渲染 + 布局探针 + 截图 | `com.microsoft.playwright` |
| `CardComplianceChecker` | @Component | 文本规则 + 布局探针登记 | 无 |
| `CardAsyncService` | @Service | 异步编排 + SSE 进度 | `@Async("cardExecutor")` |
| `CardPage` | PO | 卡片页面记录 | MyBatis-Flex |
| `CardPageMapper` | Mapper | 卡片持久化 | MyBatis-Flex |
| `CardController` | Controller | REST 端点 + 安全闸门 | CardAsyncService, CardService |

### 3.3 与现有架构的关系

- **独立包**：`com.example.aipassagecreator.card`，不与 ExportService 耦合
- **复用基础设施**：CosService（上传）、SseEmitterManager（进度推送）、QuotaService（配额）、ModelRouter、ObservabilityConfig
- **复用安全模式**：@AuthCheck + @RateLimit + 归属校验（完全对齐 ArticleController/SkillController）
- **复用方法论**：MethodologyRegistry 读取平台配置（cardStyle / minChars / maxChars）

---

## 4. 分页引擎设计（CardStructurePlanner）

### 4.1 输入标准化

1. 输入源固定用 `fullContent`（含图片 URL 的完整内容）
2. 分页前执行占位符清理：正则 `\{\{\s*(IMAGE|ICON)_PLACEHOLDER_\d+\s*\}\}` 剔除残留占位符
3. 空内容 → 业务异常 `PARAMS_ERROR("文章内容为空，无法生成卡片")`
4. mainTitle 为空 → 兜底取 `topic`

### 4.2 分页算法

```
1. 封面页：mainTitle + subTitle + coverImage（null 时用纯色渐变底）
2. 按 flexmark AST 解析 Markdown 为块级元素
3. 逐块填充：
   - 检测 ## 标题 → 新内容页
   - 首个 ## 之前的引言段归入封面页
   - 代码块/图片块/表格 → 不可分割单元，整块移入下页
   - 无 ## 标题 → 按自然段落（空行分隔）聚页
   - 每页 ≤ 500 字（中文），超限按句号断句
4. 页数上限 20 页，超限报错
5. 输出 List<PagePlan>：{pageNo, type(COVER/CONTENT), title, contentMd, contentHtml}
```

### 4.3 确保预览与生成一致性

`CardStructurePlanner.plan()` 定义为**无副作用纯函数**（仅依赖 `fullContent` 快照），预览与生成共用同一份 `plan()` 调用，确保分页结果一致。

---

## 5. 模板与渲染设计（CardTemplateEngine + CardRenderPipeline）

### 5.1 模板风格

| 风格 | key | 特点 | 色值 |
|---|---|---|---|
| 奶油信息卡 | warm | 暖色调、圆角卡片、柔和阴影 | #FFF5E6 / #F5E6CC |
| 极简蓝白 | minimal | 冷色调、大留白、细边框 | #FFFFFF / #E8F0FE |
| 自由风格 | free | 几何块、渐变背景、现代感 | 动态渐变 |

模板文件位置：`src/main/resources/templates/cards/{warm,minimal,free}.html`

### 5.2 安全渲染

```
CardRenderPipeline.render(plan, style)
  └── 从池获取 Playwright Browser 实例 (Semaphore 2~3 并发)
       ├── BrowserContext context = browser.newContext()
       ├── page.setJavaScriptEnabled(false)
       ├── page.route("**", route -> route.abort())  // 拒绝所有外部请求
       ├── page.setContent(html, new Page.SetContentOptions().setWaitUntil("load"))
       │
       ├── // DOM 布局探针
       │   Object result = page.evaluate("({
       │       overflow: document.body.scrollHeight > window.innerHeight,
       │       elements: Array.from(document.querySelectorAll('.card-page')).map(el => {
       │           const r = el.getBoundingClientRect();
       │           return { x: r.x, y: r.y, w: r.width, h: r.height };
       │       })
       │   })");
       │   complianceReport = layoutProbe.check(result);
       │
       ├── if (overflow || elementClipped) → SKIP screenshot, 标记 fail
       │   else → page.screenshot({ type: "png", fullPage: false }) → byte[]
       │
       └── finally { context.close(); }
```

### 5.3 渲染约束

| 项 | 值 | 说明 |
|---|---|---|
| 视口尺寸 | 1080×1920 | 竖版全屏 |
| deviceScaleFactor | 2 | 输出 2160×3840（高清） |
| 单页超时 | 15s | `page.setContent()` + `waitForLoadState` |
| 整批超时 | 60s | 超时整批标记 FAILED |
| 并发上限 | 3 | `Semaphore(3)` 全局 |
| 输出 PNG 大小上限 | 2MB/张 | 超限丢弃并告警 |
| 字体 | `Noto Sans CJK SC` / `PingFang SC` | Docker 预装 `fonts-noto-cjk` |
| HTML 转义 | `th:text` 禁止 `th:utext` | 正文/标题全部转义 |

### 5.4 模板安全

- 全部正文/标题用 `th:text`（HTML 自动转义），禁止 `th:utext`
- Markdown→HTML 用 flexmark 安全扩展（默认转义 + 禁用 `htmlBlock`）
- `<img src>` 协议过滤：拒绝 `javascript:` / `data:` 协议
- 模板内联 CSS（不依赖外部资源）

---

## 6. 合规检查器（CardComplianceChecker）

### 6.1 双层检查

**层 1：文本层（pre-render，纯逻辑检查）**
| 规则 | 阈值 | 级别 | 数据源 |
|---|---|---|---|
| `TitleLengthRule` | 标题≤20字 | warning | mainTitle |
| `ContentLengthRule` | 每页≤1000字 | error | PagePlan.contentMd |
| `SectionCountRule` | 大纲章节数≥3 | warning | Article.outline JSON |
| `LinkCheckRule` | 链接必含域名 | error | 正则 `\[.*\]\((.*)\)` |
| `PlaceholderRule` | 无残留占位符 | error | 正则 `\{\{.*PLACEHOLDER.*\}\}` |

**层 2：布局层（post-render，Playwright 页面内探针）**
| 规则 | 判定 | 级别 | 方法 |
|---|---|---|---|
| `LayoutOverflowRule` | overflow | error | `scrollHeight > innerHeight` |
| `LayoutClippingRule` | 元素裁切 | error | `getBoundingClientRect()` 越界 |
| `LayoutOverlapRule` | 元素重叠 | warning | 矩形相交检查 |

### 6.2 门禁语义

- **error 级**：硬门禁，任一页不通过 → 整批拒绝，不落库不上传
- **warning 级**：软建议，不影响生成，但结果写入 `ComplianceReport` 用户可查看

---

## 7. 持久化设计（CardPage）

### 7.1 表结构

```sql
create table if not exists article_card (
    id bigint auto_increment primary key,
    task_id varchar(64) not null,
    page_no int not null,
    page_type varchar(16) default 'CONTENT' not null, -- COVER / CONTENT
    style varchar(16) not null,                        -- warm / minimal / free
    image_url varchar(512) null,
    image_key varchar(256) null,                       -- COS object key
    width int default 1080,
    height int default 1920,
    bytes int default 0,                               -- PNG 字节数
    status varchar(16) default 'PENDING' not null,     -- PENDING/COMPLETED/FAILED
    compliance_report text null,                       -- JSON
    error_message text null,
    render_ms int default 0,
    create_time datetime default CURRENT_TIMESTAMP,
    update_time datetime default CURRENT_TIMESTAMP,
    unique key uk_task_page (task_id, page_no)
);
```

### 7.2 幂等语义

- 同 `task_id` 重复 generate → `DELETE ... WHERE task_id = ?` + `INSERT`（先删后插）
- 幂等基于 `(task_id, page_no)` 唯一键

### 7.3 COS 存储

- Bucket 设为私有读写（`x-cos-acl: private`）
- 对象键：`cards/{taskId}/{pageNo}_{style}_{uuid}.png`
- 返回给前端：预签名 URL（`generatePresignedUrl`，过期时间 1h）
- 上传前校验 `PNG bytes ≤ 2MB`

---

## 8. 端点设计

| 端点 | 方法 | 鉴权 | 限流 | 配额 | 说明 |
|---|---|---|---|---|---|
| `/article/cards/preview` | POST | `@AuthCheck(mustRole="user")` | `@RateLimit(limit=5, window=60)` | 扣 1 | 同步，渲染前 2 页，返回 `List<CardUrl>` |
| `/article/cards/generate` | POST | `@AuthCheck(mustRole="user")` | `@RateLimit(limit=3, window=60)` | 扣 1 | 异步，返回 `{taskId, progressUrl}` |
| `/article/cards/{taskId}` | GET | `@AuthCheck(mustRole="user")` | — | — | 查询已生成卡片列表（含状态） |

**所有端点统一执行**：
1. `@AuthCheck(mustRole="user")` → 登录校验
2. `articleService.getByTaskId(taskId)` → 文章存在性
3. `article.getUserId().equals(loginUser.getId()) || ADMIN` → 归属校验
4. `article.getStatus() == COMPLETED` → 状态门禁
5. `quotaService.checkAndConsumeQuota(loginUser, "卡片生成配额不足")` → 配额扣减（generate + preview）
6. 失败 → `quotaService.refundQuota(loginUser)`
7. Redis 单用户日上限：`INCR cards:{userId}:{yyyyMMdd}`，TTL 次日，上限 100 页/日

---

## 9. 异步化设计

### 9.1 线程池

在 `AsyncConfig.java` 增加 `cardExecutor`：
```java
@Bean("cardExecutor")
public Executor cardExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("card-exec-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    return executor;
}
```

### 9.2 异步管线

```
CardAsyncService.generateCards(taskId, style, loginUser)
  └── @Async("cardExecutor")
       ├── 1. 更新状态为 PROCESSING
       ├── 2. CardStructurePlanner.plan() → 分页
       ├── 3. CardComplianceChecker.textCheck() → 硬门禁
       ├── 4. 逐页: CardRenderPipeline.render() → PNG bytes
       ├──   每页完成后: SSE 推送进度
       ├── 5. 逐页: CosService.uploadBytes() → URL
       ├── 6. 逐页: CardPageMapper 持久化
       ├── 7. 更新状态为 COMPLETED
       ├── 8. SSE 推送完成消息
       └── 失败 → 状态 FAILED + errorMessage
```

---

## 10. 方法论模板扩展

### 10.1 PlatformConfig 新增字段

```java
@Data
public static class PlatformConfig {
    private String name;
    private String audience;
    private Integer minChars;
    private Integer maxChars;
    private String style;          // 文章风格描述（保留）
    private String cardStyle;      // 新增：卡片风格 (warm/minimal/free)
    private Map<String, Integer> evaluationWeights;
}
```

### 10.2 mergePlatform 补合并

`MethodologyRegistry.mergePlatform()` 增加：
```java
merged.setCardStyle(child.getCardStyle() != null
        ? child.getCardStyle() : parent.getCardStyle());
```

### 10.3 default.yaml 新增 platform 段

```yaml
# 新增到 default.yaml 尾部
platform:
  name: default
  minChars: 200
  maxChars: 1000
  cardStyle: warm
```

### 10.4 xiaohongshu.yaml 填充

```yaml
name: xiaohongshu
description: 小红书爆款方法论
version: 1.0
parent: default
creationDimensions:
  - key: emotionalTrigger
    name: 情绪触发点
    guidance: 击中读者的什么情绪（共鸣/好奇/焦虑）？
  - key: goldenSentence
    name: 金句
    guidance: 金句从内容自然生长，可独立转发
  - key: visualHook
    name: 视觉钩子
    guidance: 第一张卡片的封面图是否吸引点击？图文并茂
titleStrategies:
  - key: curiosityGap
    name: 好奇心缺口
  - key: painResonance
    name: 痛点共鸣
evaluationDimensions:
  - key: emotionalTrigger
    name: 情感触发
    weight: 25
    rubric: 是否触及读者情绪点
  - key: goldenSentence
    name: 金句
    weight: 20
    rubric: 是否有可独立传播的金句
  - key: visualHook
    name: 视觉钩子
    weight: 15
    rubric: 视觉设计是否吸引点击
  - key: titleStrategy
    name: 标题策略命中
    weight: 10
    rubric: 标题是否命中某一种策略
platform:
  name: xiaohongshu
  audience: 小红书用户
  minChars: 300
  maxChars: 800
  style: 图文并茂、emoji 列表、如友聊天
  cardStyle: warm
  evaluationWeights:
    emotionalTrigger: 25
    goldenSentence: 20
    visualHook: 15
    titleStrategy: 10
```

### 10.5 douyin.yaml 填充（相似但略短）

```yaml
name: douyin
description: 抖音爆款方法论
version: 1.0
parent: default
creationDimensions:
  - key: hook
    name: 前 3 秒钩子
    guidance: 开头是否抓住注意力？前 3 秒定生死
  - key: emotionalTrigger
    name: 情绪触发点
    guidance: 击中读者的什么情绪
titleStrategies:
  - key: curiosityGap
    name: 好奇心缺口
  - key: dataImpact
    name: 数据冲击
evaluationDimensions:
  - key: hook
    name: 前 3 秒钩子
    weight: 30
    rubric: 开头是否吸引注意力
  - key: emotionalTrigger
    name: 情感触发
    weight: 20
    rubric: 是否触及读者情绪点
platform:
  name: douyin
  audience: 抖音用户
  minChars: 200
  maxChars: 500
  style: 口播短句、前 3 秒定生死
  cardStyle: minimal
  evaluationWeights:
    hook: 30
    emotionalTrigger: 20
```

---

## 11. 错误处理

| 场景 | 策略 |
|---|---|
| 文章不存在 / 非 COMPLETED | 前置校验抛 `BusinessException` |
| 文章内容为空 | `PARAMS_ERROR("文章内容为空，无法生成卡片")` |
| 分页超 20 页 | `OPERATION_ERROR("文章过长，最多 20 页")` |
| 合规检查 error 级不通过 | 整批拒绝，返回 `FORBIDDEN_ERROR` 含 `ComplianceReport` |
| Playwright 渲染超时/失败 | 单页重试 2 次 → 仍失败标记 FAILED，清理已上传孤儿对象 |
| COS 上传失败 | 判空 → 重试 2 次（指数退避）→ 仍失败标记 FAILED |
| 运行期间浏览器崩溃 | 连接池自动重建 + 健康检查探针 |
| 配额不足 | 明确报错，不扣减 |
| 单用户日超限 | `OPERATION_ERROR("今日卡片生成次数已达上限")` |

---

## 12. 测试策略

| 层级 | 测试内容 | 方式 |
|---|---|---|
| 单元测试 | `CardStructurePlanner` 分页算法（空/短/超长/无标题/含占位符） | JUnit 5 |
| | `CardComplianceChecker` 文本规则（标题超长/内容超限/占位符残留） | JUnit 5 |
| | `MethodologyRegistry` cardStyle 扩展 + 继承合并 | 扩展现有测试 |
| 集成测试 | `CardService` 编排 + Playwright 渲染（mock 截图） | `@SpringBootTest` + H2 |
| H2 测试 | `article_card` 表建表 + 先删后插幂等 | 同步 h2-schema.sql |
| 安全回归 | 端点 @AuthCheck / 归属校验 / 状态门禁 / 配额 | 新增安全测试 |

**验收标准**：现有 118 测试全部通过；新增测试覆盖上述场景。

---

## 13. 目录结构（新增/修改文件）

```
新增：
  src/main/java/com/example/aipassagecreator/card/
    ├── CardService.java
    ├── CardAsyncService.java
    ├── CardStructurePlanner.java
    ├── CardTemplateEngine.java
    ├── CardRenderPipeline.java
    ├── CardComplianceChecker.java
    ├── CardPage.java
    ├── CardPageMapper.java
    ├── CardController.java
    └── model/
        ├── PagePlan.java
        ├── PageResult.java
        └── ComplianceReport.java
  src/main/resources/templates/cards/
    ├── warm.html
    ├── minimal.html
    └── free.html
  sql/add_article_card.sql

修改：
  pom.xml                                    # 加 Thymeleaf / Playwright / flexmark
  src/main/java/com/example/aipassagecreator/
    ├── config/AsyncConfig.java              # 加 cardExecutor
    ├── methodology/MethodologyDefinition.java # PlatformConfig 加 cardStyle
    ├── methodology/MethodologyRegistry.java  # mergePlatform 补合并
    ├── controller/ArticleController.java    # 加卡片端点（或新建 CardController）
    └── service/impl/ArticleServiceImpl.java # 新增 getByTaskId 归属校验辅助方法
  src/main/resources/
    ├── methodology/default.yaml             # 加 platform 段
    ├── methodology/xiaohongshu.yaml         # 完整填充
    ├── methodology/douyin.yaml              # 完整填充
    └── sql/h2-schema.sql                    # 加 article_card 表
```

---

## 14. 风险与缓解

| 风险 | 缓解 |
|---|---|
| Playwright 浏览器二进制未安装 | 构建期 `playwright install chromium --with-deps` + `@PostConstruct` 自检 |
| 中文渲染乱码 | Docker 预装 `fonts-noto-cjk` + 模板显式 `font-family` 回退链 |
| 渲染并发 OOM | `Semaphore(3)` 限并发 + 单页超时 15s + 整批超时 60s |
| COS 预签名 URL 过期 | 返回时用 1h 过期，前端拉取时下载到本地或 CDN 缓存 |
| 模板内容 XSS | `th:text` 转义 + JS disabled + CSP + 协议过滤四层防护 |
| 长文章分页过多 | 20 页上限，超限报错 |
| 用户并发刷配额 | `@RateLimit` + 配额 + 日上限三重闸门 |