# ai-passage-creator — AI 智能文章生成平台

AI 驱动的全栈文章创作平台：选题→标题→大纲→内容生成→卡片渲染→多平台发布。
详见 [README.md](README.md)。

---

## 核心工程原则 (Core Principles)

> 基于 [vibe-hub.org/anti-ai-flavor](https://vibe-hub.org/anti-ai-flavor) + [vibe-coding-ai-rules](https://github.com/obviousworks/vibe-coding-ai-rules)

| 原则 | 含义 | AI 行为要求 |
|------|------|-----------|
| **Clarify Before Coding** | 先理解需求再写代码 | 意图不清时主动提问，禁止盲目实施 |
| **Simplicity First** | 选最简单可行的方案 | 复杂模式需明确理由。可读 > 精巧 |
| **Security By Default** | 默认安全 | 校验所有输入。无硬编码密钥。纵深防御 |
| **Test-Driven Thinking** | 从测试角度思考设计 | 代码必须可测试。写代码同时写测试 |

### Trust Spectrum — 根据代码关键性决定 AI 自主程度

| 代码类型 | AI 自主度 | 审查强度 |
|---------|----------|---------|
| 🔴 安全关键 (认证/授权/XSS/加密) | 仅建议 | 手动审查 + SAST |
| 🟠 算法核心 (计费/排序/匹配) | 可实施，必带测试 | 测试 + 同行审查 |
| 🟡 业务逻辑 (状态机/校验) | 可实施，需测试覆盖 | 集成测试 |
| 🟢 样板代码 (CRUD/配置) | 高自主 | 自动测试 + 10% 人工抽查 |

### AI 代码提交前必查

- [ ] 我读过并理解了 diff 的每一处变更
- [ ] 没有不必要的抽象 (1 个实现的 Interface 不抽)
- [ ] 注释解释 "为什么" 而非 "做了什么"
- [ ] 用户可见的错误消息用中文，简洁直接
- [ ] 变量名 ≤3 个单词
- [ ] 没有冗余的空 try-catch (log+rethrow)

---

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| **后端** | Spring Boot + Java | 3.5.13 / 21 |
| **ORM** | MyBatis-Flex (NOT MyBatis-Plus) | 1.11.1 |
| **数据库** | MySQL (生产) / H2 (测试 MODE=MySQL) | — |
| **迁移** | Flyway (生产/本地) / sql.init+H2 (测试) | Boot BOM 管理 |
| **缓存/Session** | Redis + Spring Session | — |
| **AI** | Spring AI Alibaba (DashScope/Qwen) + OpenAI Starter | 1.1.0 |
| **存储** | 腾讯云 COS | 5.6.228 |
| **支付** | Stripe | 31.2.0 |
| **文档** | Knife4j (OpenAPI 3) | 4.4.0 |
| **渲染** | Playwright (Java) → HTML→PNG | 1.61.0 |
| **前端** | Vue 3 + TypeScript + Vite | 3.5 / 5.8 / 7 |
| **UI** | Ant Design Vue 4 + ECharts 6 | — |
| **状态管理** | Pinia 3 | — |
| **Markdown/XSS** | marked 17 + DOMPurify 3.4 | — |
| **测试** | JUnit 5 + Mockito (后端) / Playwright + Node test (前端) | — |

## 前后端架构图

> **每次大的新功能修改后更新此图**，保持与代码行为一致。

### 分层架构

```
                    ┌──────────────────────────────────────┐
                    │         Nginx (80) / Vite (5173)     │
                    │   SPA: Vue 3 + TypeScript + AntDV4   │
                    │   Pinia (状态) / Vue Router (路由)    │
                    └──────────────┬───────────────────────┘
                                   │ HTTP / SSE
                    ┌──────────────▼───────────────────────┐
                    │   Spring Boot 3.5 (port 8567)        │
                    │   context-path: /api                  │
                    ├───────────────────────────────────────┤
                    │ Controller 层 (薄, 仅校验+派发)       │
                    │ Article / Skill / ApiKey / Approval   │
                    │ Workspace / Publish / Analytics / RAG │
                    ├───────────────────────────────────────┤
                    │   Service 层 (业务逻辑+事务)          │
                    │   ArticleAsyncService (创作流程)      │
                    │   SkillExecutionService (技能引擎)    │
                    │   RagService (向量检索)               │
                    │   ApprovalService / PublishService    │
                    ├───────────────────────────────────────┤
                    │   Mapper 层 (MyBatis-Flex)            │
                    │   ArticleMapper / SkillExecutionMapper│
                    │   UserMapper / WorkspaceMemberMapper  │
                    ├───────────────────────────────────────┤
                    │   外部服务                             │
                    │   DashScope AI (Qwen + embedding)     │
                    │   Agnes AI (OpenAI 兼容, 降级)        │
                    │   LangSearch (联网搜索)               │
                    │   腾讯云 COS (存储)                   │
                    │   Stripe (支付)                       │
                    │   Supabase pgvector (RAG 向量库)      │
                    └───────────────────────────────────────┘
```

### 数据流：文章创作全链路

```
用户输入选题 ─→ Vue ArticleCreatePage ─→ POST /api/article/create
                    │
                    ▼
        ArticleAsyncService (异步)
          ├─ agent1: 生成标题 → TitleOption[]
          ├─ agent2: 生成大纲 → OutlineSection[]
          ├─ agent3: 生成正文 → Markdown content
          ├─ 质量门检测 (anti-AI-flavor + auto-detox)
          ├─ saveArticleContent (落库)
          ├─ updateStatus(COMPLETED)
          ├─ RAG indexArticleAsync (向量嵌入)
          ├─ 爆款评分 (VIP/Admin)
          └─ SSE: QUALITY_CHECKED → ALL_COMPLETE
                    │
                    ▼
        用户 SSE 流式接收 → 前端渲染完成态
```

### 数据流：Skill 技能引擎

```
SkillExecutePage (Vue) ─→ POST /skill/{name}/execute
                    │
                    ▼
        SkillExecutionService (异步)
          ├─ SkillRegistry: skill.yaml → StateGraph
          ├─ 每阶段: PromptTemplate → ModelRouter → ChatGPTModel
          │   ├─ 主模型: agnes-2.5-flash (OpenAI 兼容)
          │   └─ 降级: dashscope (Qwen)
          ├─ 工具绑定: webSearch → LangSearch API
          ├─ HITL 确认: 阶段输出确认 → SSE AWAITING_CONFIRMATION
          ├─ 完成 → RAG indexSkillAsync (向量嵌入)
          └─ SSE: skill.started → progress → phase_complete → complete
                    │
                    ▼
        前端流式展示进度 + 结果渲染
```

### 数据流：RAG 向量检索

```
文章 COMPLETED ──@Async(ragExecutor)──▶ RagService.indexArticle
                                          │ 入库清洗(去HTML/指令行) + TokenTextSplitter(800/200)
                                          │ DashScope embedding
                                          ▼
                                 VectorStore (Supabase pgvector / 内存降级)
                                          ▲
Skill SUCCESS ──@Async(ragExecutor)──▶ RagService.indexSkill  │   (索引接线见阶段二)
                                          │                     │
文章删除 ───────────────────────────▶ RagService.deleteByTaskId (条纹锁+isDelete守卫)
                                          │
前端(相关文章/历史参考) ────────────────▶ RagController.search (相似度阈值+分数归一化)
                                          │
创作 Agent(标题/大纲/正文) ──────────▶ RagAugmentationService.augment
                                          │ searchChunks(15) → DashScopeRerankModel 重排 → top5
                                          ▼
                                   「【参考资料】软参考块」注入 prompt
                                          │
                                    rag_reference 表存库 + SSE RAG_REFERENCE_FOUND
                                    → 详情页 GET /rag/references/{taskId} 溯源
```

### 前端路由表

| 路由 | 页面 | 权限 | 说明 |
|------|------|------|------|
| `/` | HomePage | 无 | 营销首页 + 快捷入口 |
| `/create` | ArticleCreatePage | 需登录 | 创作主流程 |
| `/article/list` | ArticleListPage | 需登录 | 文章历史列表 |
| `/article/:taskId` | ArticleDetailPage | 需登录 | 文章详情 + 审批 + 相关文章 |
| `/article/:taskId/cards` | CardPage | 需登录 | 卡片渲染管理 |
| `/article/:taskId/publish` | PublishPage | 需登录 | 多平台发布排期 |
| `/skill` | SkillCenterPage | 无 | 技能中心 |
| `/skill/:name` | SkillExecutePage | 需登录 | 执行技能 |
| `/skill/history` | SkillExecutionHistoryPage | 需登录 | 技能执行历史 |
| `/approval` | ApprovalPage | 需登录 | 审批工作台 |
| `/analytics` | AnalyticsPage | 需登录 | 分析仪表盘 |
| `/workspace` | WorkspaceListPage | 需登录 | 协作空间列表 |
| `/workspace/:id` | WorkspaceDetailPage | 需登录 | 空间详情+成员管理 |
| `/apikey` | ApiKeyPage | 需登录 | API Key 管理 |
| `/admin/userManage` | UserManagePage | admin | 用户管理 |
| `/admin/statistics` | StatisticsPage | admin | 数据统计 |
| `/admin/toolbox` | ToolboxPage | admin | 熔断器+Webhook 测试 |
| `/user/login` | UserLoginPage | 无 | 登录 |
| `/user/register` | UserRegisterPage | 无 | 注册 |
| `/vip` | VipPage | 需登录 | 会员购买 |

### 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| ORM | MyBatis-Flex | 非 MyBatis-Plus，项目已投资 Flex 生态 |
| 向量库 | Supabase+pgvector | 托管 Postgres 零运维，pgvector 扩展成熟 |
| 降级向量库 | 内存 SimpleVectorStore | 无 Supabase 凭据时开发不阻塞（`FilterSupportSimpleVectorStore` 补过滤删除） |
| Embedding | DashScope text-embedding | 复用现有 DASHSCOPE_API_KEY，零额外成本 |
| 重排 | DashScope gte-rerank（RerankModel） | RAG 最高 ROI 改进，检索 top15 → 重排 → top5 |
| 模型主备 | agnes 主 / dashscope 降级 | Agnes 延迟更低，Dashscope 更稳定 |
| AI 框架 | Spring AI Alibaba StateGraph | 多阶段图编排，支持 HITL+工具调用 |
| 用户隔离 | 普通用户只能检索自己，admin 全站 | 数据隐私 + 管理需求平衡 |
| 认证 | Session + Redis | 传统 session 简单可靠，Redis 分布在多实例 |

---

## 关键命令

### 后端（项目根目录）

```bash
mvn spring-boot:run                    # 启动 (端口 8567, context-path /api)
mvn test -Dspring.profiles.active=test # 运行后端测试 (H2, session=none)
mvn clean package -DskipTests          # 仅打包
```

### 前端（`frontend/` 目录）

```bash
npm run dev                # 开发服务器 (Vite HMR)
npm run type-check         # TypeScript 类型检查 (vue-tsc --build)
npm run build              # 完整构建: type-check → vite build → 包体积检查
npm run test               # 全部测试: skill 单元测试 + Playwright E2E
npm run test:skill         # Node 内置 test runner 单元测试
npm run test:ui            # Playwright E2E
npm run check              # 完整质量闸门: lint → build → test → 性能
npm run lint:check         # ESLint 检查
npm run format             # Prettier 格式化
```

### 质量闸门（CI 等价）

```bash
# 在提交前运行 ← CI 的等价本地检查
cd frontend && npm run check
cd .. && mvn test
```

---

## 项目结构

```
ai-passage-creator/
├── src/main/java/com/example/aipassagecreator/
│   ├── controller/       # REST 控制器
│   ├── service/          # 业务逻辑层
│   ├── mapper/           # MyBatis-Flex Mapper
│   ├── model/            # DTO/VO/Entity
│   ├── config/           # Spring 配置
│   ├── skill/            # AI Skill 定义与执行
│   ├── card/             # 卡片渲染 (Playwright HTML→PNG)
│   ├── publish/          # 内容多平台发布
│   ├── agent/            # AI Agent 编排
│   ├── manager/          # 第三方服务管理 (COS/AI)
│   ├── aop/              # 切面 (日志/鉴权)
│   ├── exception/        # 全局异常处理
│   ├── constant/         # 常量定义
│   ├── enums/            # 枚举
│   └── utils/            # 工具类
├── src/main/resources/
│   ├── application.yml   # 主配置
│   ├── db/migration/     # Flyway 迁移 (V1__baseline + V{n}__desc.sql)
│   └── sql/              # 测试 schema (h2-schema.sql) + 历史迁移 SQL 参考
├── src/test/             # JUnit 5 + Mockito 测试
├── frontend/src/
│   ├── api/              # API 调用 + OpenAPI 类型定义
│   ├── components/       # 全局共享组件
│   ├── pages/            # 页面组件 (按模块分目录)
│   ├── stores/           # Pinia 状态管理
│   ├── router/           # Vue Router 配置
│   ├── utils/            # 工具函数 (sse / markdown / article / date)
│   ├── constants/        # 前端常量
│   ├── styles/           # 全局样式
│   ├── access.ts         # 路由守卫 (权限校验)
│   ├── request.ts        # Axios 封装 (60s超时, 拦截器)
│   └── main.ts           # 入口
├── frontend/tests/       # Playwright E2E + 技能状态单元测试
├── .github/workflows/ci.yml  # CI: mvn test + Docker 构建
└── scripts/git-push.sh   # 单 remote 推送脚本 (github / origin=Gitee)
```

---

## 架构约定

### 后端分层 (自上而下)

```
Controller → Service → Mapper → DB
     ↓           ↓
   DTO/VO       Entity
```

- **Controller**: 仅参数校验 + 调用 Service + 返回结果。禁止业务逻辑。
- **Service**: 业务逻辑 + 事务管理 (`@Transactional`)。
- **Mapper**: MyBatis-Flex BaseMapper。复杂查询写在 XML 或注解 SQL。
- **DTO/VO**: Controller 入参用 DTO，响应用 VO。不要直接暴露 Entity。
- **全局异常处理**: `GlobalExceptionHandler` (`@RestControllerAdvice`) 统一处理。

### 前端组件树 (创作页)

```
ArticleCreatePage
├── InputStage          # 选题输入
├── TitleSelectingStage # 标题选择 (空状态保护!)
├── OutlineEditingStage # 大纲编辑 (Sortable 拖拽)
├── ContentGeneratingStage  # 内容生成 (SSE 流式)
└── CompletedState      # 完成态 (下载/发布)
```

### 跨层数据流

```
User Input → Vue → POST /api/article/create → SSE taskId → EventSource → 流式渲染
```

---

## 领域术语

| 术语 | 含义 | 对应代码 |
|------|------|---------|
| **Task** | 文章生成任务 (从选题到完稿) | `ArticleController.createTask` |
| **Phase/Stage** | 文章创作阶段 | `INPUT → TITLE_SELECTING → OUTLINE_EDITING → CONTENT_GENERATING → COMPLETED` |
| **Title Option** | AI 生成的标题候选方案 (含主副标题) | `TitleOption { mainTitle, subTitle }` |
| **Outline Section** | 大纲章节 (含要点列表 points[]) | `OutlineSection { section, title, points }` |
| **Skill** | 可复用的 AI 技能定义 (JSON schema) | `SkillController / SkillExecutePage` |
| **Card Render** | Playwright 渲染 HTML→PNG 卡片图 | `card/` 包 + `CardController` |
| **Viral Quality** | 爆款质量评分 | `viral_quality` 相关表 |
| **Approval** | 内容审批工作流 | `ApprovalController` |

---

## 代码规范

> 后端和前端详细规范分别在 `.claude/rules/java-backend.md` 和 `.claude/rules/vue-frontend.md` 中，按需加载。
> 安全红线在 `.claude/rules/security.md` 中。

### 后端摘要

- 分层：Controller(薄) → Service(重) → Mapper(MyBatis-Flex)
- 命名：Java 标准 (PascalCase 类 / camelCase 方法变量)
- API：RESTful + Knife4j OpenAPI 文档
- 测试：`ClassNameTest`，`methodName_scenario_expectedResult()` 模式
- 依赖注入：构造器注入 (Lombok `@RequiredArgsConstructor`)

### 前端摘要

- 组件：Vue 3 Composition API `<script setup lang="ts">`
- 类型：TypeScript strict，API 类型由 `npm run openapi2ts` 自动生成
- 状态：跨组件用 Pinia store，局部状态用 `ref` / `computed`
- 样式：SCSS scoped，Ant Design Vue 组件优先

### Git

- **Conventional Commits**: `type(scope): description`
- **类型**: `feat` / `fix` / `docs` / `refactor` / `test` / `chore` / `style` / `perf`
- **Scope**: `frontend` / `backend` / `card` / `skill` / `infra` / `db`
- **分支**: `dev` (开发) / `master` (生产) / `track/*` (特性分支)
- **推送**: 脚本每次只推单个 remote，GitHub 用 `github`，Gitee 用 `origin`（`gitee` 名不存在）：
  ```bash
  bash scripts/git-push.sh github dev   # → GitHub
  bash scripts/git-push.sh origin dev   # → Gitee
  ```
  查看 remote 列表: `git remote -v`

---

## 开发工作流

### Bug 修复流程 (严格执行)

```
1. 审计 → 用 Grep/Grep 扫描相关代码区
2. 创建 Task → TaskCreate 记录修复项，标注严重度 (HIGH/MEDIUM/LOW)
3. 逐项修复 → 先读后改 (Read → Edit)，每次只改一个关注点
4. type-check → cd frontend && npm run type-check (零容忍 TS 错误)
5. test → cd .. && mvn test (零容忍测试失败)
6. commit → Conventional Commits 格式 + Co-Authored-By: Claude
7. push → `bash scripts/git-push.sh github dev` (GitHub) + `bash scripts/git-push.sh origin dev` (Gitee)
```

### Plan-before-code (禁止盲目实施)

```
当任务涉及 >3 文件或架构决策时：
1. EnterPlanMode 编写方案
2. 用户审批后 ExitPlanMode
3. 按计划逐步实施
```

### Post-implement 验证

```
每次实施后自动触发:
1. npm run type-check (前端)
2. npm run build (前端 — vite build 才能发现纯 CSS `//` 注释等编译错误，type-check 检测不到)
3. mvn test (后端)
4. 验证无新增 ESLint 告警
```

### Commit 质量门禁

```
提交前确认:
- [ ] type-check 零错误
- [ ] test 全绿
- [ ] git status 只包含预期文件
- [ ] commit message 符合 Conventional Commits
- [ ] Co-Authored-By: Claude <noreply@anthropic.com>
- [ ] 已通过 AI 代码气味检查 (见 anti-ai-flavor 检查清单)
- [ ] 无 Accept-Without-Read — 理解 diff 中每一处变更
- [ ] 无 Copy-Paste Sprawl — 搜索确认无重复实现
```

---

## 已知暗坑 (三轮审计沉淀)

> **每次修改相关代码前先读此清单，避免重蹈覆辙。**

| # | 坑 | 部位 | 正确做法 |
|---|-----|------|---------|
| 1 | **XSS** — raw `marked` 绕过 DOMPurify | 任何 v-html | 必须用 `@/utils/markdown` 包装，永不直接 import `marked` |
| 2 | **SSE EventSource 泄漏** — 未关闭旧连接就建新连接 | ArticleCreatePage | `closeSSE()` 后再 `connectSSE()` |
| 3 | **SSE 生命周期** — async await 后打开 SSE，组件已卸载 | SkillExecuteSurface | await 后检查 `unmounted` 标记 |
| 4 | **SSE 瞬断永久关闭** — `onerror` 直接 close 无重连 | sse.ts connectSSE | 1 次重连后降级到 onError |
| 5 | **Polling 循环泄漏** — `setTimeout` 前 set null，await 后未检查卸载 | SkillExecuteSurface | `if (unmounted) return` 守卫 |
| 6 | **loginUser 过期残留** — code≠0 不重置用户状态 | loginUser store | code≠0 / fetch 异常时 `createDefaultUser()` |
| 7 | **loginUser 无超时** — fetch 阻塞路由守卫 | loginUser store | AbortController + 10s 超时 |
| 8 | **Props 浅拷贝** — `points: item.points ?? []` 共享引用 | OutlineEditingStage | `points: item.points ? [...item.points] : []` |
| 9 | **分页乱序响应** — 快速切换分页无请求排序 | UserManagePage / ArticleListPage | `fetchSeq` 递增 + 响应后校验 |
| 10 | **下载竞态** — `revokeObjectURL` 在 `click()` 后立即调用 | article.ts / resultActions.ts | append 到 body + `setTimeout(revoke, 100)` |
| 11 | **空列表死端** — `titleOptions=[]` 时 `selectedIndex=0` 导致按钮永久禁用 | TitleSelectingStage | 空时显示 empty state + `canConfirm` 提前 return false |
| 12 | **Sortable 未销毁** — 组件卸载时拖拽实例未 `destroy()` | OutlineEditingStage | `onBeforeUnmount` 中 `sortableInstance.destroy()` |
| 13 | **双重提交** — `startExecution` 无 `submitting` 守卫 | SkillExecuteSurface | `if (submitting.value) return` |
| 14 | **纯 CSS 中写 `//` 注释** — `<style scoped>` 无 `lang="scss"` 时 `//` 非法，vite build 失败 | 任何 .vue style 块 | style 无 `lang="scss"` 时用 `/* */` 注释；改 scss 前确认已声明 lang |

---

## CI/CD

- **触发**: push `master`/`dev`/`track/*`, PR → `master`/`dev`
- **流程**: JDK 21 → `mvn test` (H2, session=none) → Docker build (仅 master)
- **环境变量**: CI 中 `spring.session.store-type=none` (无 Redis 可用)

---

@.claude/rules/security.md
@.claude/rules/java-backend.md
@.claude/rules/vue-frontend.md
@.claude/rules/anti-ai-flavor.md
