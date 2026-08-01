# 贡献指南 — AI Passage Creator (灵犀写作)

> 本文档约定项目协作规范，适用于**所有开发者与 AI 协作代理**。
> 目标：提交信息可检索、变更可追溯、评审有依据。

---

## 1. 提交信息规范 (Conventional Commits)

采用 [Conventional Commits 1.0](https://www.conventionalcommits.org/zh-hans/v1.0.0/) 标准。

### 1.1 格式

```
<type>(<scope>): <subject>
<BLANK LINE>
<body>
<BLANK LINE>
<footer>
```

### 1.2 Type 枚举表

| type | 含义 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(skill): 新增 research 技能多轮确认` |
| `fix` | 缺陷修复 | `fix(auth): 修复 SSE 端点 401 问题` |
| `refactor` | 重构（非 bug 非功能） | `refactor(agent): DashScopeChatModel → ChatModel` |
| `perf` | 性能优化 | `perf(statistics): Redis 缓存聚合查询` |
| `test` | 测试相关 | `test(skill): 补齐 HITL 集成测试` |
| `docs` | 文档变更 | `docs: 补充 Skill 引擎架构说明` |
| `style` | 格式/样式（不影响逻辑） | `style: 统一 import 顺序` |
| `chore` | 构建/依赖/杂务 | `chore(deps): 升级 okhttp 4.12.0` |
| `ci` | CI 配置 | `ci: 新增 GitHub Actions 流水线` |
| `build` | 构建系统 | `build: 添加 Dockerfile 多阶段构建` |
| `revert` | 回滚 | `revert: 回滚配额 CAS 改动` |

### 1.3 Scope 命名

当前项目的 scope 枚举：

| scope | 模块 |
|-------|------|
| `skill` | Skill 引擎（skill/ 包） |
| `agent` | 多智能体编排（agent/ 包） |
| `article` | 文章链路（service/ 中 article 相关） |
| `auth` | 认证/权限（aop/ + UserService） |
| `payment` | Stripe 支付 |
| `image` | 配图策略 |
| `workspace` | 协作空间 |
| `approval` | 审批流 |
| `publish` | 发布排期 |
| `analytics` | 数据分析 |
| `webhook` | 出站通知 |
| `infra` | 基础设施（Docker/CI/Redis/限流/熔断） |
| `deps` | 依赖管理 |

> 无明确模块归属时省略 scope：`feat: 描述`。

### 1.4 Subject 规则

- 一句话概括，**动词开头**，简明准确
- 不超过 **72 字符**
- 中文或英文均可，但**同一仓库保持一致**（当前主流为中文）
- 不写句号结尾
- 示例：`feat(article): 新增五维质量评分` ✅
  `feat(article): 新增五维质量评分。` ❌

### 1.5 Body 规则

- 换行后详述**做了什么**与**为什么**（动机）
- 每行 ≤72 字符
- 用列表呈现变更点，便于评审
- 若改动简单可省略 body

### 1.6 Footer / Trailer

- **AI 协作提交必须附加**：
  ```
  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- 破坏性变更标注：
  ```
  BREAKING CHANGE: GET /skill/{id}/result 返回值结构调整
  ```

---

## 2. 单一职责原则

**一次提交只做一件事。** 判断标准：

- ✅ 独立提交：`fix(security): 修复质量评分端点 IDOR`
- ❌ 混合提交：`feat(B1+D1): 质量评分 + Docker 容器化`（两件事混在一起）

> 若一次开发包含多个独立功能，应拆分多个 commit；若已混合，评审时指出并拆分。

---

## 3. 分支规范

| 分支类型 | 命名 | 说明 |
|----------|------|------|
| 集成主线 | `master` / `dev` | 稳定可发布 |
| 功能分支 | `track/<track>-<feature>` | 如 `track/a-skill-engine` |
| 修复分支 | `fix/<描述>` | 如 `fix/rate-limit-redis` |

**流程**：从 `dev` 开 `track/*` → 开发 → 本地验证（`mvn verify` 全绿）→ PR → 评审 → 合入 `dev`。

---

## 4. 提交前自检清单

- [ ] `mvn verify` 全部测试通过
- [ ] 前端（如有改动）`npm run build` 通过
- [ ] 提交信息符合 Conventional Commits 格式
- [ ] 单次提交只做一件事
- [ ] 无硬编码密钥（检查 `application*.yml`）
- [ ] 无未处理的安全审查告警
- [ ] 新端点归属校验（防 IDOR）
- [ ] 新增表已同步 `sql/h2-schema.sql` + `sql/*.sql`

---

## 5. 紧急/临时提交约定

- 推送前务必清理：`feat`, `fix` 等临时描述不得进入主线
- 冲突解决后需在 body 注明 `merge-conflict: <说明>`
