# 贡献指南 — AI Passage Creator (灵犀写作)

> 本文档约定项目协作规范，适用于**所有开发者与 AI 协作代理**。
> 目标：提交信息可检索、变更可追溯、评审有依据。
>
> **规范依据**：[Conventional Commits 1.0](https://www.conventionalcommits.org/zh-hans/v1.0.0/)、
> [AngularJS/EC Commit Guidelines](https://ec.europa.eu/component-library/docs/conventions/git/)、
> [Semantic Commit Messages](https://gist.github.com/joshbuchea/6f47e86d2510bce28f8e7f42ae84c716)、
> [thoughtbot commit 指南](https://thoughtbot.com/blog/the-art-of-writing-meaningful-git-commit-messages)。

---

## 1. 提交信息规范 (Conventional Commits)

### 1.1 完整格式结构

```
<type>(<scope>): <subject>       ← header（必填）
                                 ← 空行
<body>                            ← 动机说明（可选，复杂变更必填）
                                 ← 空行
<footer>                          ← BREAKING CHANGE / issue / trailer（可选）
```

**结构要点：**
- **header 必填**；scope、body、footer 可选但推荐
- **任何一行 ≤ 100 字符**（EC/Angular 规范），GitHub 显示更友好

### 1.2 Type 枚举表（EC/Angular 9 种 + 扩展）

| type | 含义 | SemVer 关联 | 示例 |
|------|------|-------------|------|
| `feat` | 新功能 | MINOR | `feat(skill): 新增 research 技能多轮确认` |
| `fix` | 缺陷修复 | PATCH | `fix(auth): 修复 SSE 端点 401 问题` |
| `docs` | 文档变更 | — | `docs: 补充 Skill 引擎架构说明` |
| `style` | 格式（不影响逻辑） | — | `style: 统一 import 顺序` |
| `refactor` | 重构（非 bug 非功能） | — | `refactor(agent): DashScopeChatModel → ChatModel` |
| `perf` | 性能优化 | — | `perf(statistics): Redis 缓存聚合查询` |
| `test` | 测试相关 | — | `test(skill): 补齐 HITL 集成测试` |
| `chore` | 构建/工具/杂务 | — | `chore(deps): 升级 okhttp 4.12.0` |
| `build` | 构建系统 | — | `build: 添加 Dockerfile 多阶段构建` |
| `ci` | CI 配置 | — | `ci: 新增 GitHub Actions 流水线` |
| `revert` | 回滚 | — | 见 §1.7 |

> ⚠️ 只有 `feat`/`fix` 影响版本号；其余 type 不隐含版本变化（除非含 BREAKING CHANGE）。

### 1.3 Scope 命名

**命名约定**（EC/Angular）：
- 一律 **小写**，多个单词用连字符 `-`
- 只能指定**一个** scope（禁止 `feat(B1+D1)` 混合）
- 是模块/影响范围的上下文，而非任务编号

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

### 1.4 Subject 规则（50/72 规则 + 语义）

| 规则 | 标准 | 来源 |
|------|------|------|
| **长度上限** | **≤ 50 字符**（推荐）/ ≤ 72 硬上限 | 50/72 规则 |
| **语态** | **祈使句现在时**："add" 而非 "added"/"adds" | EC/Angular |
| **首字母** | 英文**小写**开头（如 `feat: add ...`） | EC/Angular |
| **句尾** | **无句号** | EC/Angular |
| **语义** | 描述**"为什么"**而非仅"做了什么" | thoughtbot |
| **语言** | 中文/英文皆可，**同一仓库保持一致**（当前中文） | 项目约定 |

示例：
- ✅ `feat(article): 新增五维质量评分`
- ❌ `feat(article): 新增五维质量评分。`（句号）
- ❌ `feat(article): 新增五维质量评分并且优化了导出逻辑`（超长）
- ❌ `feat: Added new feature`（过去时）

### 1.5 Body 规则

- 详述**动机**（为什么改）与**与旧行为的对比**（EC/Angular）
- 每行 **≤ 72 字符**
- 用列表呈现变更点，便于评审
- 示例：
  ```
  feat(skill): 新增 research 技能多轮确认

  之前的 HITL 仅支持单点确认；research 有 search+summary
  两个阶段，需支持多次暂停/续跑。
  - SkillExecutionRegistry 支持二次暂停刷新 TTL
  - resume() 从检查点续跑后再次检查中断点
  ```

### 1.6 Footer / Trailer

**破坏性变更（BREAKING CHANGE）— 三种写法（任意 type 均可携带）：**

| 写法 | 示例 | 关联版本 |
|------|------|----------|
| Footer 标注 | `BREAKING CHANGE: extends 键现在用于扩展配置文件` | MAJOR |
| 感叹号 | `feat!: 移除旧版 API` | MAJOR |
| scope+感叹号 | `feat(api)!: 接口返回结构调整` | MAJOR |

**Issue 引用：**
```
Closes #123
```

**AI 协作提交必须附加（git trailer 格式）：**
```
Co-Authored-By: Claude <noreply@anthropic.com>
```

### 1.7 Revert 规范

```
revert: <原提交的 header>

This reverts commit <原提交 hash>.
```

---

## 2. 单一职责原则

**一次提交只做一件事。** 判断标准：

- ✅ 独立提交：`fix(security): 修复质量评分端点 IDOR`
- ❌ 混合提交：`feat(B1+D1): 质量评分 + Docker 容器化`（两件事混在一起）

> 若一次开发包含多个独立功能，应拆分多个 commit；若已混合，评审时指出并拆分。

---

## 3. Gitmoji（可选增强）

若团队偏好视觉化，可在 conventional type 前附加 gitmoji（来自 [gitmoji.dev](https://gitmoji.dev/specification)）：

| emoji | type | 含义 |
|-------|------|------|
| ✨ | feat | 新功能 |
| 🐛 | fix | 缺陷修复 |
| ♻️ | refactor | 重构 |
| 📝 | docs | 文档 |
| ⚡ | perf | 性能 |

> ⚠️ 注意：gitmoji 会干扰 commitlint 解析，二者择一。**本项目默认纯 conventional，不启用 gitmoji。**

---

## 3. 分支规范

| 分支类型 | 命名 | 说明 |
|----------|------|------|
| 集成主线 | `master` / `dev` | 稳定可发布 |
| 功能分支 | `track/<track>-<feature>` | 如 `track/a-skill-engine` |
| 修复分支 | `fix/<描述>` | 如 `fix/rate-limit-redis` |

**流程**：从 `dev` 开 `track/*` → 开发 → 本地验证（`mvn verify` 全绿）→ PR → 评审 → 合入 `dev`。

---

## 4. 工具链（可选，逐步引入）

| 工具 | 作用 | 引入条件 |
|------|------|----------|
| **commitlint** (`@commitlint/config-conventional`) | 校验 type 枚举 + header 格式 | 前端已引入 node 后 |
| **husky** | pre-commit/commit-msg 钩子管理 | 同上 |
| **Commitizen** (`cz-cli`) | 交互式提交向导 | 团队偏好 |
| **semantic-release** | 自动从 commit 生成版本 + CHANGELOG | 达到发布节奏后 |
| **本项目 git 钩子** | `.githooks/commit-msg` 轻量校验 | 已启用（无 node 依赖） |

> 当前项目为 **Java + Maven 为主**，采用 `.githooks/commit-msg` bash 钩子（无 node 依赖）。
> 若前端需要更严格的校验，可在 `frontend/` 内引入 commitlint。

---

## 5. 提交前自检清单

- [ ] `mvn verify` 全部测试通过
- [ ] 前端（如有改动）`npm run build` 通过
- [ ] header 符合 `<type>(<scope>): <subject>` 格式
- [ ] subject ≤ 50 字符（≤ 72 硬上限）
- [ ] subject 为祈使句、无句号、首字母（英文）小写
- [ ] body 每行 ≤ 72 字符，说明动机
- [ ] BREAKING CHANGE 已用 `!` 或 footer 标注
- [ ] 单次提交只做一件事
- [ ] 无硬编码密钥（检查 `application*.yml`）
- [ ] 无未处理的安全审查告警
- [ ] 新端点归属校验（防 IDOR）
- [ ] 新增表已同步 `sql/h2-schema.sql` + `sql/*.sql`

---

## 6. 推送规范（GitHub 稳定性）

### 6.1 环境配置（一次性）

```bash
# 根治大提交推送失败（RPC failed / HTTP 413 / 慢速超时）
git config --global http.postBuffer 524288000
git config --global http.lowSpeedLimit 1000
git config --global http.lowSpeedTime 60

# 启用本地钩子（提交格式 + 推送安全检查）
git config core.hooksPath .githooks
```

### 6.2 推送命令

```bash
# 推荐：带重试的推送脚本（指数退避，最多 3 次）
bash scripts/git-push.sh github dev

# 或直接推送
git push github dev
```

### 6.3 pre-push 自动检查

每次推送前自动拦截：

| 检查 | 拦截条件 |
|------|----------|
| 大文件 | 推送中新增 >2MB 文件 |
| 硬编码密钥 | 疑似生产密钥（`sk_test_` / `whsec_` / `AKID`） |
| 构建产物 | 提示（不拦截） |

### 6.4 常见推送失败

| 症状 | 处理 |
|------|------|
| `RPC failed; HTTP 413` | 检查 `http.postBuffer` 是否 500MB |
| `connection reset` | 重试脚本（网络抖动）或检查低速率配置 |
| `pre-push` 拦截 | 按提示处理（加 .gitignore / 移除密钥） |
| 推送后 CI 失败 | 检查 `.github/workflows/ci.yml` |

---

## 7. 紧急/临时提交约定

- 推送前务必清理：`feat`, `fix` 等临时描述不得进入主线
- 冲突解决后需在 body 注明 `merge-conflict: <说明>`
