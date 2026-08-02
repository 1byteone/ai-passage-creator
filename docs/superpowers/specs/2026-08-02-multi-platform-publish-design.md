# 多平台内容分发子系统 设计文档（第三阶段）

- **日期**：2026-08-02
- **分支**：dev
- **状态**：已批准
- **前置依赖**：Phase 1（爆款方法论引擎）、Phase 2（图文卡片生成子系统）

---

## 1. 背景与目标

Phase 1/2 已完成「爆款方法论引擎」与「图文卡片生成子系统」，文章可被方法论引导创作并渲染为多风格竖版卡片。**Phase 3 目标是使文章可适配转换到多平台格式，并通过排期调度执行分发。**

### 1.1 参考来源

- **SyncCaster**：PlatformAdapter 适配器模式、统一内容模型、适配器注册
- **wechat_content_factory**：声明式创作流水线、平台差异化适配

### 1.2 范围

- 扩展已有 `PublishSchedule` 基础设施（排期表/Controller/Service 已存在）
- 新建 `publish/platform/` 包：平台适配器 SPI + 三平台实现（公众号/小红书/抖音）
- 实现 `executeDuePublishes()` 定时扫描→转换→校验→落库
- 各平台适配器做内容格式转换（Markdown→平台富文本/纯文本/口播文案）
- **不做浏览器自动化发布**（Phase 3 聚焦内容侧适配）

---

## 2. 架构

```
PublishController (扩展: schedule 加 platform 字段)
    ↓
PublishServiceImpl
    ├── schedule() → 创建排期记录(+platform)
    ├── executeDuePublishes() → @Scheduled 每分钟扫描
    │   └── ContentPublisher.convertAndValidate()
    │       ├── ArticleService.getByTaskId → Article
    │       ├── MethodologyRegistry.get → PlatformConfig(minChars/maxChars/style)
    │       ├── PlatformAdapter.convert(Article, MethodologyDef) → PlatformContent
    │       └── PlatformAdapter.validate(content) → List<ComplianceIssue>
    └── → 存储 adapterOutput(JSON) + 更新 status

PlatformAdapterRegistry
    └── Map<String, PlatformAdapter> beans (Spring 自动注入 List<PlatformAdapter>)
```

### 2.1 新增组件

| 组件 | 文件 | 职责 |
|---|---|---|
| `PlatformAdapter` | 接口 | `platform()`, `convert()`, `validate()` |
| `WechatAdapter` | @Component | 公众号 Markdown→富文本 |
| `XiaohongshuAdapter` | @Component | 小红书 Markdown→纯文本+emoji+话题 |
| `DouyinAdapter` | @Component | 抖音 Markdown→口播文案 |
| `PlatformAdapterRegistry` | @Component | 注册中心，按平台名查适配器 |
| `ContentPublisher` | @Component | 编排转换+校验 |
| `PlatformContent` | record | title, body, topics, metadata |
| `ComplianceIssue` | record | rule, level, message |

### 2.2 修改组件

| 组件 | 改动 |
|---|---|
| `PublishSchedule` PO | +platform(wechat/xiaohongshu/douyin), +adapterOutput(JSON), +contentTitle |
| `PublishService` / impl | schedule 签名字段调整, executeDuePublishes 实现 |
| `PublishController` | schedule 端点加 platform/methodologyName 参数 |
| `sql/h2-schema.sql` | publish_schedule 加列 |

---

## 3. 接口设计

### 3.1 PlatformAdapter

```java
public interface PlatformAdapter {
    /** 平台标识（与 methodology name 对齐） */
    String platform();

    /** Markdown 正文 → 平台格式 */
    PlatformContent convert(String title, String markdown, MethodologyDefinition def);

    /** 平台规则校验 */
    List<ComplianceIssue> validate(PlatformContent content, MethodologyDefinition def);
}
```

### 3.2 三平台适配规则

| 平台 | adapter.platform() | convert 行为 | 字数范围 |
|---|---|---|---|
| wechat | "wechat" | Markdown→HTML富文本（保留段落/加粗/引用块） | 1500-3000 |
| xiaohongshu | "xiaohongshu" | Markdown→纯文本+emoji列表 + 从大纲提取5话题标签 | 300-800 |
| douyin | "douyin" | Markdown→口播短句（取首段+核心观点摘取） | 200-500 |

### 3.3 validation 规则

| 规则 | 级别 | 适用平台 |
|---|---|---|
| `LengthRule` | ERROR | 全部 — 字数超 platform.minChars/maxChars |
| `TopicCountRule` | WARNING | 小红书 — 话题数 < 5 |
| `HookRule` | WARNING | 抖音 — 无前3秒钩子判定 |
| `ImageRule` | WARNING | 公众号 — 无coverImage（公众号需封面） |

---

## 4. 数据模型

### 4.1 PublishSchedule 扩展

```sql
ALTER TABLE publish_schedule ADD COLUMN platform varchar(32) DEFAULT 'wechat' NOT NULL;
ALTER TABLE publish_schedule ADD COLUMN content_title varchar(256) NULL;
ALTER TABLE publish_schedule ADD COLUMN adapter_output json NULL;
ALTER TABLE publish_schedule ADD COLUMN methodology_name varchar(64) DEFAULT 'default' NULL;
```

`adapterOutput` JSON 格式：
```json
{
  "title": "转换后的标题",
  "body": "转换后的正文（富文本/纯文本/口播脚本）",
  "topics": ["话题1", "话题2"],
  "issues": [{"rule": "LengthRule", "level": "WARNING", "message": "超20字"}],
  "metadata": {"platform": "xiaohongshu", "charCount": 450}
}
```

---

## 5. 排期执行

```
@Scheduled(fixedRate = 60_000) // 每分钟扫描一次
executeDuePublishes():
  1. SELECT * FROM publish_schedule
     WHERE publish_at <= NOW() AND status = 'PENDING'
     ORDER BY publish_at ASC LIMIT 10;

  2. FOR EACH schedule:
     a. article = articleService.getByTaskId(schedule.articleTaskId)
       → 归属校验(article.userId == schedule.createdBy || admin)
     b. def = methodologyRegistry.get(schedule.methodologyName)
       → 读取 platform.minChars/maxChars/style
     c. adapter = adapterRegistry.get(schedule.platform)
       → 不存在? 标记 FAILED + errorMessage
     d. content = adapter.convert(article.getMainTitle(), article.getContent(), def)
     e. issues = adapter.validate(content, def)
       → ERROR 级: 标记 FAILED + 错误信息
       → 否则: 存储 content+issues 到 adapterOutput
     f. 更新 status='PUBLISHED', published_at=now()

  3. 返回处理数
```

---

## 6. 端点变更

| 端点 | 变更 |
|---|---|
| `POST /publish/schedule` | 新增字段：platform(必填)、methodologyName(可选) |
| `POST /publish/cancel/{id}` | 不变 |
| `GET /publish/article/{taskId}` | 不变 |
| `GET /publish/status/{scheduleId}` | 新增：查询单条排期的转换结果 |

---

## 7. 安全设计

- 所有端点继承现有 @AuthCheck(mustRole="user") + 归属校验
- `executeDuePublishes` 对每条记录做 `article.userId == schedule.createdBy` 归属校验
- platform 参数校验：`PlatformAdapterRegistry.get(platform)` 不存在 → PARAMS_ERROR
- adapterOutput 存库时校验 JSON 大小 ≤ 64KB

---

## 8. 测试策略

| 层 | 测试 | 方式 |
|---|---|---|
| 单元 | WechatAdapter.convert Markdown→HTML | JUnit |
| | XiaohongshuAdapter 话题提取 | JUnit |
| | PlatformAdapterRegistry 注册发现 | @SpringBootTest |
| 集成 | ContentPublisher.convertAndValidate 全链路 | @SpringBootTest + H2 |
| | executeDuePublishes 定时执行 | @SpringBootTest + Mock ArticleService |

---

## 9. 文件清单

### 新增文件
```
src/main/java/com/example/aipassagecreator/publish/
  └── platform/
      ├── PlatformAdapter.java
      ├── PlatformContent.java
      ├── ComplianceIssue.java
      ├── PlatformAdapterRegistry.java
      ├── WechatAdapter.java
      ├── XiaohongshuAdapter.java
      ├── DouyinAdapter.java
      └── ContentPublisher.java
src/main/resources/sql/add_publish_platform.sql
```

### 修改文件
```
src/main/java/com/example/aipassagecreator/model/po/PublishSchedule.java
src/main/java/com/example/aipassagecreator/service/PublishService.java
src/main/java/com/example/aipassagecreator/service/impl/PublishServiceImpl.java
src/main/java/com/example/aipassagecreator/controller/PublishController.java
src/main/resources/sql/h2-schema.sql
```
