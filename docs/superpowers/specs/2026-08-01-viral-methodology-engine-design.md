# 爆款方法论引擎 设计文档（第一阶段）

- **日期**：2026-08-01
- **分支**：track/a-skill-engine
- **状态**：已批准（含三轮专业审计修订吸收）
- **关联规划**：第一阶段「爆款方法论引擎」，后续阶段为图文卡片生成、多平台分发、知识库素材库

---

## 1. 背景与目标

`ai-passage-creator` 是 Spring Boot 3 + Java 21 + MyBatis-Flex + Spring AI 的多 Agent AI 内容创作平台，已具备多 Agent 编排（StateGraph）、Skill 引擎（skill.yaml 声明式）、SSE 流式、通用质量评分、导出、支付配额、缓存限流熔断等能力。

第一阶段目标是构建「爆款方法论引擎」：以 YAML 方法论模板为**单一配置源**，驱动标题/正文 Agent 的**创作引导**与文章的**爆款维度评测**，并通过「评测 → 定向改写 → 复评 → 回退」闭环迭代提升文章爆款力。同时修复审计发现的两处现网安全洞，确保新能力全程遵循项目既有的鉴权、配额、限流与可观测规范。

### 1.1 方法论来源

参考 nashsu/Viral_Writer_Skill 的爆款方法论：11 内容洞见维度、标题五策略（好奇心缺口/数据冲击/痛点共鸣/反常识/社交货币）、平台差异化表达。本设计将其沉淀为可配置、可评测、可反哺的模板体系。

---

## 2. 范围

### 2.1 In-scope（第一阶段交付）

1. `methodology/` 模板目录 + `default.yaml`（完整）+ `wechat.yaml`（含平台规则）+ `xiaohongshu.yaml`/`douyin.yaml`（骨架占位）
2. `MethodologyDefinition` + `MethodologyRegistry`（扫描加载 + extends 继承 + 兜底 + 维度 key 一致性校验）
3. 共享 `YamlResourceLoader`（同时加固现有 `SkillRegistry`）
4. `MethodologyPromptAssembler`（创作引导：标题策略段 + 创作维度段，双执行路径统一注入）
5. `ContentQualityService.evaluateViral()`（爆款维度评测，与通用 5 维合并单次 LLM 调用）
6. `MethodologyRefiner`（反哺闭环：top-2 低分维度定向改写 → 复评 → 无提升回退，≤3 轮）
7. article_quality 表扩展列 + H2/生产 schema 同步
8. 新端点安全闸门（`@AuthCheck` + `@RateLimit` + 归属校验 + 配额扣减）
9. **前置安全修复**：`/article/execution-logs` IDOR、`/article/rewrite` prompt 注入
10. 单元测试 + 集成测试（H2）

### 2.2 Out-of-scope（后续阶段）

- 小红书/抖音平台规则完整填充（第二阶段配合图文卡片）
- 多平台分发（第三阶段）
- 知识库素材库（第四阶段）
- 方法论模板后台管理界面（动态编辑）

---

## 3. 架构

### 3.1 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│              methodology/*.yaml 模板文件层                    │
│  default.yaml (通用)  wechat.yaml (公众号)  骨架占位           │
│  声明：creationDimensions / titleStrategies /                │
│        evaluationDimensions / platform(可选)                 │
└─────────────────────────────────────────────────────────────┘
                              │ 共享 YamlResourceLoader
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ MethodologyRegistry（内存不可变 Map，启动期加载）              │
│  extends 物化 + 环检测 + 内置兜底 default + 维度 key 校验     │
└─────────────────────────────────────────────────────────────┘
         │                        │                        │
         ▼                        ▼                        ▼
┌──────────────┐   ┌──────────────────────┐   ┌──────────────────┐
│ 创作引导       │   │ 爆款评测（扩展）       │   │ 反哺闭环          │
│ Methodology   │   │ ContentQualityService│   │ MethodologyRefiner│
│ PromptAssembler│  │ .evaluateViral()     │   │ top-2 维度改写     │
│ 双路径 6 处注入 │   │ 合并单次 LLM 调用     │   │ →复评→无提升回退   │
└──────────────┘   └──────────────────────┘   └──────────────────┘
         │                        │
         ▼                        ▼
   StateGraph 编排            article_quality 表
   (orchestrator + fallback)  (score_type/viral_*/version_no)
```

### 3.2 组件清单

| 组件 | 类型 | 职责 | 关键设计 |
|---|---|---|---|
| `YamlResourceLoader` | @Component | 共享 SnakeYAML（SafeConstructor + LoaderOptions）+ `classpath*:` 扫描 + 文件大小限制 | 同时供 SkillRegistry 复用 |
| `MethodologyDefinition` | POJO | 模板 Java 映射 | 含 creationDimensions/titleStrategies/evaluationDimensions/platform |
| `MethodologyRegistry` | @Component | 启动期加载为不可变 Map，`get(name)` 内存查名，缺失抛 `IllegalArgumentException` | extends 物化 + 环检测 + 代码级兜底 default + 维度 key 一致性校验 |
| `MethodologyPromptAssembler` | @Component | `buildTitleGuidance(name)` / `buildContentGuidance(name)` 组装提示段 | 插入到输出格式约束之前 |
| `ContentQualityService.evaluateViral()` | 服务 | 爆款维度评测 | 与通用 5 维合并单次 LLM 调用；标题+正文显式入 prompt；成功才落库 |
| `MethodologyRefiner` | 服务 | 反哺闭环 | 独立驱动，≤3 轮，top-2 低分维度，复评无提升 `revertTo` |
| article_quality 扩展列 | 表 | 评测结果持久化 | score_type 判别 + version_no 幂等 |

### 3.3 与现有架构的关系

- **独立于 SkillRegistry**：methodology 是「数据/提示配置」，无 phases/图，不塞入 SkillRegistry；但加载骨架（扫描 + Yaml + 缓存 + fail-open）抽为共享 `YamlResourceLoader`。
- **双执行路径**：`article.agent.orchestrator.enabled` 控制 orchestrator（`ArticleAgentOrchestrator` + 三个 Agent）与 fallback（`ArticleAgentService`）两条 prompt 构建路径，方法论段在两条路径共 6 处组装点统一追加。

---

## 4. 模板 Schema 设计

### 4.1 文件位置

```
src/main/resources/methodology/
├── default.yaml       # 通用方法论（完整）
├── wechat.yaml        # 公众号（extends default + platform 规则）
├── xiaohongshu.yaml   # 骨架占位（第二阶段填充）
└── douyin.yaml        # 骨架占位（第二阶段填充）
```

### 4.2 Schema（default.yaml）

```yaml
name: default
description: 通用爆款方法论
version: 1.0

# 创作维度（事前引导，注入正文 Agent prompt）
creationDimensions:
  - key: coreViewpoint
    name: 核心观点
    guidance: 读者只能记住一句话，是哪句？开头点明，全文围绕展开
  - key: persuasion
    name: 说服策略
    guidance: 数据/故事/权威/类比/社会认同，选 2-3 种组合 + 主策略
  - key: emotionalTrigger
    name: 情绪触发点
    guidance: 击中读者的什么情绪（共鸣/好奇/焦虑/向往）？
  - key: goldenSentence
    name: 金句
    guidance: 金句从内容自然生长，结尾提炼，可独立转发
  - key: interactionHook
    name: 互动钩子
    guidance: 开放提问/争议观点/投票/晒图，让读者有话想说

# 标题策略（生成标题时按策略标注）
titleStrategies:
  - key: curiosityGap
    name: 好奇心缺口
  - key: dataImpact
    name: 数据冲击
  - key: painResonance
    name: 痛点共鸣
  - key: counterIntuitive
    name: 反常识
  - key: socialCurrency
    name: 社交货币

# 评测维度（事后评测；key 必须与创作维度对齐）
evaluationDimensions:
  - key: emotionalTrigger
    name: 情感触发
    weight: 20
    rubric: 是否触及读者情绪点，引起共鸣或向往
  - key: goldenSentence
    name: 金句
    weight: 15
    rubric: 是否有可独立传播的金句，且从内容自然生长
  - key: interactionHook
    name: 互动钩子
    weight: 15
    rubric: 是否有引导读者参与互动的设计
  - key: persuasion
    name: 说服策略
    weight: 15
    rubric: 是否有效运用数据/故事/类比等说服手段
  - key: titleStrategy
    name: 标题策略命中
    weight: 10
    rubric: 标题是否命中某一种策略且有效
```

### 4.3 extends 继承（wechat.yaml）

```yaml
extends: default
platform:
  name: wechat
  audience: 公众号读者
  minChars: 1500
  maxChars: 3000
  style: 长论证、信息密度高
  evaluationWeights: { emotionalTrigger: 20, goldenSentence: 20 }
```

### 4.4 Schema 约束（Registry 启动期校验，违反 fail-fast）

1. **维度 key 规范化**：每个维度（评测/创作）一个规范化 key，评测 rubric / 创作 guidance / Refiner 映射三处引用同一 key；评测维度与创作维度可存在差异但交叉引用的 key 必须一致。
2. **权重校验**：`weight ∈ (0, 100]`，Σweight > 0；平台覆盖采用「按维度 patch + 新增维度追加」，覆盖后重新归一化。
3. **extends 约束**：DFS + visited 集合做环检测；继承采用**物化（materialize）**——加载时把继承结果解析为完整副本存入缓存，运行期只读；`default` 缺失时回退代码级内置兜底模板。
4. **安全**：SafeConstructor + LoaderOptions（maxAliases ≤ 50、codePointLimit ≤ 1MB、nestingDepthLimit ≤ 50）；单文件 ≤ 100KB，超限告警跳过。
5. **缺失兜底**：`get(name)` 只走内存 Map，不存在抛 `IllegalArgumentException`（与 SkillRegistry 一致）；绝不将用户输入拼入任何文件路径。

---

## 5. 创作引导设计

### 5.1 触发链路

`methodology` 从创建请求进入全链路：

```
ArticleCreateRequest.methodology (默认 "default")
  → ArticleServiceImpl.createArticleTaskWithQuotaCheck(...) 透传
  → article 表 methodology 列持久化
  → ArticleAsyncService.executePhase2/3 从 DB 回填 state.setMethodology(...)
  → ArticleAgentOrchestrator 三处 phase inputs.put("methodology", ...)
  → createKeyStrategyFactory() 注册 KEY_METHODOLOGY → ReplaceStrategy
  → TitleGeneratorAgent / ContentGeneratorAgent 读取 state.value("methodology")
```

### 5.2 注入方式（双路径统一）

- `MethodologyPromptAssembler.buildTitleGuidance(name)`：组装标题策略段（5 策略 + 要求标注 `strategyKey`）。
- `MethodologyPromptAssembler.buildContentGuidance(name)`：组装创作维度段（各维度 guidance）。
- **注入位置**：方法论段插入到「输出格式约束」（"请直接返回 JSON/Markdown"）**之前**，避免破坏 JSON 输出。
- **双路径**：orchestrator 路径（`TitleGeneratorAgent`/`OutlineGeneratorAgent`/`ContentGeneratorAgent`）与 fallback 路径（`ArticleAgentService` 的 agent1/agent2/agent3）共 6 处统一追加，由 `MethodologyPromptAssembler` 提供，不在单个 Agent 内硬编码。
- 现有 `getStylePrompt(style)` 保留，方法论段与风格段并列追加。

### 5.3 标题策略可回溯

- `ArticleState.TitleOption` 增加 `strategyKey` 字段。
- `TitleGeneratorAgent` prompt 要求每个标题标注命中的 `titleStrategies[].key`。
- 用户最终选中标题的 strategyKey 记录到 ArticleState/文章表，供评测直接引用。

---

## 6. 爆款评测设计

### 6.1 接口

```java
// ContentQualityService 新增
ArticleQuality evaluateViral(String taskId, String methodologyName, Long loginUserId);

// 按任务 + 版本取最新爆款评测（score_type 过滤）
ArticleQuality getLatestViral(String taskId);
```

### 6.2 评测流程

1. **前置校验**：文章存在且归属 loginUserId（service 二次校验，纵深防御）；`article.status` 为 COMPLETED；methodologyName 白名单查名（fail-fast，不静默回退）；`titleStrategyHit` 在 `article.mainTitle` 为空时标记 skip 不参与加权。
2. **Prompt 组装**：显式拼接 `mainTitle + subTitle + content`，标题与正文分别截断（快照策略可配置：`head|head-tail|full`，默认 head-tail）。
3. **合并调用**：通用 5 维（structure/logic/language/seo/readability）+ 爆款维度**合并为一次 LLM 调用**，返回统一 JSON：`{"structureScore":..., ..., "viral": {"emotionalTrigger":..., ...}, "titleStrategyHit":..., "suggestions":[...]}`。
4. **结果计算**：`viralScore = Σ(score_i × weight_i) / Σ(weight_i)`（分母归一化）；分数统一 `BigDecimal`/`double` 解析，入库前四舍五入；维度缺失标记 incomplete 不进入阈值判定。
5. **容错**：`tryFixJson` 提升到 `GsonUtils` 并增强（剥 ```围栏、取首尾 `{}`、截断数字兜底），评测走 `fromJsonSafe` + 修复重试；修复失败返回「评测失败」状态落库而非抛异常。
6. **落库**：成功才落库；`(task_id, version_no)` 唯一键幂等 upsert（防行爆炸）；`score_type` 区分 GENERIC/VIral。
7. **配额与可观测**：`@RateLimit` + `QuotaService.checkAndConsumeQuota`（失败 refund）+ `@AgentExecution` + `TokenUsageHolder.record` + `ObservabilityConfig.recordLlmCall` 打 dimension/methodology tag。

### 6.3 存储（article_quality 扩展列）

| 新列 | 类型 | 说明 |
|---|---|---|
| `score_type` | varchar(16) NOT NULL DEFAULT 'GENERIC' | 判别通用/爆款评测 |
| `user_id` | bigint NULL | 行级归属 |
| `viral_score` | decimal(5,2) NULL | 爆款加权综合分 |
| `viral_scores` | json NULL | 各爆款维度分 |
| `title_strategy_hit` | varchar(32) NULL | 标题策略命中 |
| `methodology_used` | varchar(64) NULL | 评测所用方法论 |
| `content_hash` | varchar(64) NULL | 内容快照 hash（幂等） |
| `version_no` | int NOT NULL DEFAULT 1 | 评测绑定版本（幂等） |

`getLatest` 增加 `id desc` 二级排序（create_time 秒级并发不稳定）；`getLatestViral` 按 `score_type` 过滤。

---

## 7. 反哺闭环设计（MethodologyRefiner）

### 7.1 流程

```
MethodologyRefiner.refine(taskId, methodologyName, loginUserId)
  │ 二次归属校验
  ▼
evaluateViral 获取最新爆款评测
  ▼
低于阈值(维度 < 60 或 viralScore < 70)且未达 maxRounds(≤3)
  ▼
取 top-2 低分维度
  ▼
查对应创作维度 guidance（评测/创作共享规范化 key）
  ▼
ArticleRewriteService.rewriteSection(taskId, sectionLocator, guidance, loginUserId)
  │ 定向段落改写（或 v1 退化为整篇改写）
  ▼
复跑 evaluateViral
  ▼
viralScore 提升？ → 保留新版本
  ▼
无提升 → revertTo 回退上一版本，记录 delta
  │ 每轮写版本历史（article_versions.qualityScore 回填 viralScore）
  ▼
结束（达 maxRounds 或无低分维度）
```

### 7.2 关键约束

- **独立驱动**：MethodologyRefiner 独立于 evaluateViral，由编排层（Controller 或独立任务）驱动，杜绝运行时递归。
- **终止条件**：每任务 ≤ 3 轮；每轮只改最弱 1-2 个维度；复评无提升即 `revertTo`。
- **改写能力**：优先 `rewriteSection`（章节标题/段落索引定位）；v1 退化为整篇重写；去掉 10000 字截断或按块处理。
- **幂等/并发**：同任务并发评测用 Redis 锁串行；改写用版本号乐观锁。
- **配额**：每轮扣配额，失败自动 refund。

---

## 8. 安全设计（安全闸门 + 前置修复）

### 8.1 前置修复（阻塞项，先修再上设计）

1. **`/article/execution-logs/{taskId}` IDOR**：加 `@AuthCheck(mustRole="user")` + `getByTaskId` 归属校验（admin 豁免）。该端点泄露 AgentLog 完整 prompt/输入/输出。
2. **`/article/rewrite` prompt 注入**：用户 `modifySuggestion` 改独立 `SystemMessage` + 输出约束，与内容分离。

### 8.2 新端点安全闸门

| 端点 | 鉴权 | 限流 | 归属校验 | 配额 |
|---|---|---|---|---|
| `POST /article/evaluate-viral` | `@AuthCheck(mustRole="user")` | `@RateLimit(limit=5, window=60, key="viral_evaluate")` | controller `getByTaskId` + userId 比对 + admin 豁免；service 二次校验 | `QuotaService` 扣减，失败 refund |
| `POST /article/refine` | `@AuthCheck(mustRole="user")` | `@RateLimit(limit=3, window=60, key="viral_refine")` | 同上 | `QuotaService` 扣减，失败 refund |

### 8.3 其他

- methodologyName 走内存白名单查名 fail-fast，绝不拼文件路径（防路径穿越）。
- SnakeYAML SafeConstructor + LoaderOptions（防 billion-laughs DoS）；SkillRegistry 同步加固。
- `tryFixJson` 提升到 GsonUtils 工具层（公共方法）。

---

## 9. 错误处理

| 场景 | 策略 |
|---|---|
| 方法论模板不存在 | `get(name)` 抛 `IllegalArgumentException`（fail-fast）；未指定时默认 `default` |
| default 缺失/解析失败 | 代码级内置兜底 default；启动期 fail-fast（dev 可跳过） |
| YAML 解析失败（非 default） | per-resource fail-open，跳过 + log.error |
| 维度 key 不一致 / 权重非法 | 启动期校验 fail-fast |
| LLM 评测 JSON 解析失败 | `GsonUtils.fromJsonSafe` + 增强 tryFixJson；失败返回「评测失败」落库 |
| 维度缺失 | 标记 incomplete，不进入阈值判定，suggestions 注明 |
| 文章不存在/非 COMPLETED | 前置校验抛明确业务异常 |
| 评测超时/配额不足 | 配额不足明确报错；失败不扣配额（或自动 refund） |
| 反哺闭环无提升 | `revertTo` 回退，记录 delta，终止 |

---

## 10. 测试策略

| 层级 | 测试内容 | 方式 |
|---|---|---|
| 单元测试 | YamlResourceLoader 加载/大小限制/DoS 防护 | JUnit + Mockito |
| | MethodologyRegistry extends 物化/环检测/兜底/维度 key 校验 | 仿 PromptTemplateEngineTest |
| | MethodologyPromptAssembler 标题/正文段组装、注入位置 | JUnit |
| | evaluateViral 权重归一化/维度完备性/JSON 容错 | Mock ChatModel 返回固定 JSON |
| | MethodologyRefiner 轮次终止/回退判定 | Mock ArticleRewriteService |
| 集成测试 | 方法论模板真实加载 + 双路径创作引导注入 | SkillEngineIntegrationTest 风格 |
| | 评测 → 反哺闭环端到端（≤3 轮收敛） | Mock LLM，真实 Service |
| H2 测试 | article_quality 新列迁移/读写/幂等 upsert | 同步 h2-schema.sql |
| 安全回归 | execution-logs 鉴权、rewrite SystemMessage、新端点归属/配额 | 新增安全测试 |

**验收标准**：现有 155+ 测试全部通过；新增测试覆盖上述场景。

---

## 11. 目录结构（新增/修改文件）

```
新增：
  src/main/java/com/example/aipassagecreator/methodology/
    ├── MethodologyDefinition.java
    ├── MethodologyRegistry.java
    ├── MethodologyPromptAssembler.java
    └── MethodologyRefiner.java
  src/main/java/com/example/aipassagecreator/config/YamlResourceLoader.java
  src/main/resources/methodology/
    ├── default.yaml
    ├── wechat.yaml
    ├── xiaohongshu.yaml    # 骨架
    └── douyin.yaml         # 骨架
  sql/add_viral_quality.sql

修改：
  src/main/resources/sql/h2-schema.sql        # article_quality 新列同步
  src/main/java/.../controller/ArticleController.java   # 新端点 + 修复 execution-logs
  src/main/java/.../service/ContentQualityService.java / impl
  src/main/java/.../service/ArticleRewriteService.java / impl   # rewriteSection + SystemMessage
  src/main/java/.../agent/agents/TitleGeneratorAgent.java       # TitleOption.strategyKey + 方法论注入
  src/main/java/.../agent/agents/ContentGeneratorAgent.java     # 方法论注入
  src/main/java/.../agent/agents/OutlineGeneratorAgent.java     # 方法论注入
  src/main/java/.../agent/ArticleAgentOrchestrator.java         # inputs + KeyStrategy
  src/main/java/.../service/ArticleAgentService.java            # fallback 路径注入
  src/main/java/.../service/ArticleAsyncService.java            # phase2/3 回填 methodology
  src/main/java/.../service/impl/ArticleServiceImpl.java        # create 透传 methodology
  src/main/java/.../model/dto/article/ArticleCreateRequest.java
  src/main/java/.../model/dto/article/ArticleState.java         # methodology + TitleOption.strategyKey
  src/main/java/.../model/po/Article.java                       # methodology 列
  src/main/java/.../model/po/ArticleQuality.java                # 新列
  src/main/java/.../mapper/ArticleQualityMapper.java            # 幂等 upsert
  src/main/java/.../skill/SkillRegistry.java                    # 复用 YamlResourceLoader
  src/main/java/.../utils/GsonUtils.java                        # tryFixJson 提升 + 增强
  src/main/java/.../skill/parsers/JsonOutputParser.java         # 复用工具层
```

---

## 12. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 双执行路径行为漂移 | 统一 assembler，6 处注入点一致，双路径测试 |
| 主观评测维度抖动 | 合并单次调用 + 自检论证 + 复评收敛 ≤3 轮 |
| LLM 成本失控 | 配额 + 限流 + 幂等缓存 + 合并调用 + token 预算 |
| 长文截断丢信息 | head-tail 快照 + 按块改写 |
| H2/生产 schema 漂移 | 同步三处 schema + 集成测试兜底 |
| 并发评测/改写覆盖 | Redis 锁 + 版本号乐观锁 + version_no 幂等 |
