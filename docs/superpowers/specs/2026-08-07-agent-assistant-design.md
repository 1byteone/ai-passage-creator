# AI 创作助手（Agent Assistant）设计文档

> 日期：2026-08-07
> 状态：设计定稿，待转实施计划
> 范围：前端悬浮 Agent 助手 + 后端 Agent 编排链路

---

## 1. 背景与目标

### 1.1 问题

ai-passage-creator 是 AI 驱动的全栈文章创作平台（选题→标题→大纲→正文→卡片→发布）。当前用户需要打开多个页面完成创作、检索历史文章、执行技能，但**没有一个统一的对话入口**。

项目已有：
- **技能引擎**：13 个 skill + SSE 流式 + 确认节点 + 检查点续跑（Spring AI Alibaba StateGraph）
- **RAG 向量检索**：RagService（检索）+ RagAugmentationService（重排增强），但**无生成/问答能力**
- **SSE 封装**：前端 `sse.ts`（connectSSE / connectSkillSSE）
- **Session 认证**：Cookie + withCredentials

**最大缺口**：无通用对话端点、无任何聊天/悬浮窗 UI 代码、RAG 只有检索无生成。

### 1.2 目标

在平台右下角提供**悬浮式 AI 创作助手**，成为统一创作入口：既能纯对话问答、又能检索个人知识库增强回答、还能按需触发 skill 创作任务，全部在一条对话流里完成。

### 1.3 成功标准

1. 登录用户在任意页面可一键唤起助手，完成任务
2. 一条消息流内完成：对话 / RAG 增强 / skill 执行的混合编排
3. 对话历史持久化，重开抽屉可恢复
4. 游客可试用（仅纯对话 + 严格限流），登录解锁全部
5. 4 套主题（治愈绿/瑞士/clean/点阵）下视觉自然融合
6. WCAG AA 无障碍达标

---

## 2. 需求清单（已与用户对齐）

| 维度 | 决策 |
|---|---|
| 技术形态 | **项目内 Vue3 组件**，挂载 BasicLayout，不引入新框架 |
| 能力范围 | **完整 Agent**：纯对话 + RAG 检索增强 + skill 执行 |
| 交互形态 | **右下角悬浮按钮 + 抽屉面板** |
| 访问权限 | **游客可试用 + 登录完整** |
| 游客边界 | 仅纯对话 + 严格限流（5 次/分）；不检索知识库、不触发 skill |
| 编排方式 | **混合**：LLM 自动为主 + 手动 skill tab 兜底 |
| 会话持久化 | **持久化历史会话**（conversation + message 表） |
| 架构方案 | **方案 A**：独立 Agent 编排链路，复用 skill/RAG 作为工具 |

---

## 3. 系统架构（方案 A）

### 3.1 分层

```
Vue AgentChatWidget（悬浮+抽屉）
   │ POST /api/agent/chat (SSE)
   ▼
AgentController（薄：校验+派发）
   │
   ▼
AgentConversationService（编排核心）
   ├─ 意图路由：LLM 判定 → chat / RAG / skill
   ├─ 对话会话：conversation + message 持久化
   ├─ RAG 增强：复用 RagAugmentationService.augment
   └─ Skill 桥接：复用 SkillExecutionService.dispatchAndExecute
   │
   ├── SkillExecutionService（现有，工具）
   ├── RagAugmentationService（现有，工具）
   ├── AgentSseEmitterManager（新增，事件缓冲+回放，参考 SkillSseEmitterManager）
   └── conversation / message Mapper（新增）
```

### 3.2 关键决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 编排核心 | 新建 `AgentConversationService` | 不硬塞进 skill 引擎 phase 模型；多轮对话+持久会话需要独立会话层 |
| skill 复用 | `dispatchAndExecute(skillName, inputs, user)` 现成入口 | 执行/SSE/配额/检查点全部继承 |
| RAG 复用 | `RagAugmentationService.augment(query, userId)` | 检索→重排→软参考块，直接注入生成 |
| 游客隔离 | 无用户态走 `guestToken`（内存桶限流），不进库 | 纯对话无持久化，防匿名刷量 |
| SSE 管理 | 新 `AgentSseEmitterManager`（模式复刻现有 Skill 管理器） | 环形缓冲 200 条 + 订阅回放 + 10min 超时 |
| 配额 | skill 触发走现有 @RateLimit + 配额表 | 复用计费体系 |

### 3.3 数据模型（新增表）

```sql
-- 会话表
CREATE TABLE agent_conversation (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL,              -- 登录用户；游客不建会话
  title       VARCHAR(100) NOT NULL,        -- 自动摘要首条用户消息
  created_at  DATETIME NOT NULL,
  updated_at  DATETIME NOT NULL
);

-- 消息表
CREATE TABLE agent_message (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  role          VARCHAR(10) NOT NULL,       -- user / assistant
  kind          VARCHAR(16) NOT NULL,       -- text / skill / error
  content       TEXT NOT NULL,              -- 文本（assistant 为 markdown）
  meta_json     TEXT,                       -- skill 引用 / RAG 引用 / 状态
  created_at    DATETIME NOT NULL,
  is_delete     TINYINT DEFAULT 0
);
CREATE INDEX idx_conv ON agent_message(conversation_id, created_at);
```

- Flyway：`V{n}__create_agent_conversation_message.sql`（可移植 DDL）
- 测试：同步 `h2-schema.sql`（longtext 代替 json，见 memory h2-json-column-compat）
- `FlywayMigrationCompatibilityTest` 校验迁移集

### 3.4 SSE 事件协议（统一，前端一套渲染）

```
event: agent.chat_started
event: agent.text_delta     data: {"text":"..."}
event: agent.rag_reference  data: {"sources":[{"id":1,"title":"...","type":"article","createdAt":"..."}]}
event: agent.skill_started  data: {"skillName":"research","totalPhases":3}
event: agent.skill_phase    data: {"phase":1,"total":3,"name":"搜索资料","progress":40}
event: agent.skill_confirm  data: {"executionId":123,"action":"approve|modify","modifiedData":{...}}
event: agent.complete       data: {"messageId":456}
event: agent.error          data: {"message":"生成失败，请重试"}
```

- skill 内部事件由 `SkillSseEmitterManager` 桥接到本协议（`agent.skill_*`）
- 缓冲 + 订阅回放，断线 1 次重连后降级轮询

### 3.5 意图路由（混合编排）

```
用户消息
  │
  ▼ 意图判定（LLM function-calling 或规则兜底）
  ├─ 需要个人知识库 → RAG 检索增强 → 回答 + 脚注引用
  ├─ 明确 skill 意图 → 触发对应 skill → 阶段进度内联
  └─ 一般问答 → 直接流式回复
  │
  ▼ 手动兜底：前端「技能」tab 用户显式选 skill → 走 skill 桥接
```

- 兜底规则：含「总结/改写/翻译/选题/标题/大纲/脚本/调研」等关键词且上下文为空 → 触发 skill
- 失败降级：LLM 判定失败 → 按纯对话处理，不中断

### 3.6 权限与限流

| 场景 | 处理 |
|---|---|
| 游客 | 内存桶 5 次/分，无会话持久化；SSE 端点放行纯对话 |
| 登录用户 | session 鉴权；skill 触发复用现有配额/@RateLimit |
| skill 归属 | 桥接后 executionId 校验归属当前用户（复用现有逻辑） |

---

## 4. UI 设计

> 设计主线：**跟随主题的变色龙 + 书写式流式签名 + 编辑脚注引用 + 零装饰渐变**

### 4.1 视觉设计系统（全 token，零硬编码）

| 设计元素 | 映射 token | 说明 |
|---|---|---|
| 抽屉面板 | `--surface-panel` / `--glass-bg`+`--glass-blur` | 亮色主题毛玻璃；高对比主题实色（主题智能适配） |
| 用户消息 | `--surface-brand-soft` + `--text-strong` | 品牌淡底，右对齐 |
| AI 消息 | `--surface-page` + 左细边框 `--border-subtle` | 对齐+边框+角色标签三重区分（不只颜色） |
| 书写光标 | `--color-primary` | 签名元素，呼吸脉动 |
| Skill 块 | `--surface-muted` + `--color-primary` + `--state-*` | 阶段流水线工位 |
| 引用脚注 | `--surface-muted` + `--border-subtle` | 编辑脚注质感 |
| 字体 | `--font-body` 消息 / `--font-heading` 标题 / `--font-mono` 参数与代码 | 不引入新字族 |
| 渐变 | **零装饰渐变**（瑞士主题拒绝渐变）；按钮辉光做成 `--assistant-button-glow`，瑞士/点阵置 none | 避免 AI-slop |

**签名元素**：
- **书写式流式**：token 逐字浮现 + 2px 垂直光标呼吸脉动（opacity 0.35→1，1.4s ease-in-out），rAF 批处理 60fps，reduced-motion 静止
- **编辑脚注引用**：正文 `[1][2]` 上标 → 消息下方脚注条（短标签+类型徽标+时间），悬停预览摘要、点击跳来源，缺失来源显式呈现

**4 主题性格**：
| 主题 | 性格 |
|---|---|
| 治愈绿（默认） | 圆角 12px、毛玻璃、柔和阴影 |
| 瑞士甲板 | 直角 0px、实色、红黑高对比、大写标题 |
| 简约点阵 | 圆角、衬线标题、淡点阵背景 |
| 点阵终端 | 霓虹边框、等宽字体、实色深底 |

### 4.2 布局与组件结构

```
frontend/src/components/AgentChat/
├── AgentChatWidget.vue     # 悬浮按钮 + 抽屉外壳（挂 BasicLayout）
├── ConversationList.vue    # 会话切换（新对话/历史，可收起窄列）
├── MessageList.vue         # 消息滚动区（aria-live）
├── MessageItem.vue         # 单条消息分发（text/user/skill/rag/error）
├── WritingCaret.vue        # 书写式流式光标
├── SkillPhaseBlock.vue     # skill 阶段流水线 + 进度 + 折叠详情
├── CitationFootnotes.vue   # 编辑脚注条 + 悬停预览
├── ComposerBar.vue         # 输入 + 发送/停止 + 技能 tab 兜底
└── useAgentChat.ts         # SSE 协议消费 + 消息状态机（组合式函数）
```

**抽屉解剖**（桌面 400px 宽，全高；<768px 全屏）：

```
┌─ 会话 ─┐ AI 助手                  ⊗ 关闭
│ 新对话 ▾ 历史会话
│  今日 · 春日茶事文案      ●
│  昨天 · 视频脚本初稿      ·
├──────────────────────────────┤
│ ◈ 你好，我是创作助手…[1][2]     │ ← AI 消息（左对齐+左边框）
│ [1] 我的文章·AI时代 ▾          │ ← 脚注条
│            ▸ 帮我生成一个开头    │ ← 用户消息（右对齐+品牌淡底）
│ ⚙ research  ▓▓▓░░ 40%          │ ← Skill 块（工位点亮）
│ ①搜索资料➜ ②整理摘要⚪ ③生成简报⚪ │
│ ✦ 正在检索… ▼ 查看详情          │
├──────────────────────────────┤
│ [技能▾] ⌜ 写点什么… ⏎发送 ⌟     │ ← Composer
└──────────────────────────────┘
```

**消息类型（5 种，一套渲染逻辑）**：

| 类型 | 形态 |
|---|---|
| `text` AI 消息 | 左对齐 + 左边框 + 书写流式 + 可选脚注条 |
| `user` 用户消息 | 右对齐 + 品牌淡底 |
| `skill` Skill 块 | 阶段流水线 + 进度 + 状态 + 折叠详情 |
| `rag` 引用脚注 | 上标 `[n]` + 脚注条 + 悬停预览 |
| `error` / `empty` | 中文直接说明 + 重试；空态给建议问题 chips |

**Composer 关键交互**：
- Enter 发送 / Shift+Enter 换行；流式中→停止按钮（省 API 成本）
- 技能 tab：下拉选 skill → 参数表单内联展开
- 游客：显示"登录后解锁知识库与技能"轻提示

### 4.3 响应式 / 无障碍 / 错误态

**响应式**：
| 断点 | 抽屉 | 按钮 |
|---|---|---|
| ≥992px | 400px 右侧滑出全高 | 56px |
| 768–992 | 360px × 85dvh | 48px |
| <768px | 全屏 100vw×100dvh | 44px |

- `visualViewport` 适配键盘弹起；`env(safe-area-inset-bottom)` 适配刘海屏

**无障碍（AA）**：
- `aria-live="polite"` + `aria-atomic="false"`，流式防抖批量播报
- Enter/Shift+Enter、Esc 关闭、抽屉焦点陷阱、焦点返回触发按钮
- 颜色不唯一（对齐+边框+标签）；`--border-focus` 焦点环；token 文本色已 AA
- `prefers-reduced-motion` 全降级为 150ms 淡入

**错误态**：
| 场景 | 呈现 |
|---|---|
| SSE 瞬断 | 1 次重连 → 错误提示 + 重试 |
| 生成失败 | "生成失败，请重试" + 保留已生成内容 |
| 用户停止 | 保留内容 + 标记「已停止」+ 可继续 |
| 空态 | 3 个建议问题 chips |
| 引用缺失 | 「来源已不可用」显式呈现 |
| 游客受限 | "登录后解锁知识库与技能" |
| 429 | "请求过于频繁，请稍后再试" |

---

## 5. 安全（对照 security.md 红线）

| 风险 | 措施 |
|---|---|
| XSS | AI/用户内容经 `@/utils/markdown`（marked+DOMPurify）渲染，永不 raw marked |
| SSE 泄漏 | 复用 `connectSSE` 关闭逻辑；组件卸载关闭；1 次重连 |
| 竞态 | fetchSeq 防乱序；await 后检查 unmounted |
| 越权 | 会话/executionId 归属校验（复用现有）；游客无 skill/知识库权限 |
| 限流 | 游客内存桶 + 登录复用 @RateLimit/配额 |
| 投毒 | RAG 注入清洗复用现有链路 |

---

## 6. 测试策略

| 层 | 覆盖 |
|---|---|
| 后端单测 | `AgentConversationServiceTest`：意图路由、会话 CRUD、skill 桥接、游客限流 |
| 后端集成 | `AgentControllerTest`：SSE 事件流、归属校验、双模式权限 |
| 前端单测 | `useAgentChat.ts` 状态机（消息类型转换、停止/重试）—— Node test runner |
| E2E | Playwright：唤起抽屉、发消息收到流式、触发 skill 看进度、历史恢复、4 主题截图对比、游客受限提示 |

---

## 7. 里程碑

| 阶段 | 内容 |
|---|---|
| P0 地基 | conversation/message 表 + Flyway + 测试 schema |
| P1 对话链路 | AgentController + 意图路由 + SSE 协议 + 纯对话 |
| P2 RAG 增强 | 复用 augment + 脚注引用事件 |
| P3 skill 桥接 | 触发/进度内联/确认 + 手动 tab |
| P4 UI 打磨 | 书写光标 + 主题适配 + 响应式 + 无障碍 |
| P5 游客模式 | 内存限流 + 游客 SSE + 登录解锁 |

---

## 8. 参考

- [AI Chat UI Best Practices（thefrontkit 2026）](https://dev.to/greedy_reader/ai-chat-ui-best-practices-designing-better-llm-interfaces-18jj)
- [Agent UI（agno-agi）](https://www.blog.brightcoding.dev/2026/03/26/agent-ui-the-essential-chat-interface-for-ai-agents)
- [AI Citation Patterns（shapeof.ai）](https://www.shapeof.ai/patterns/citations)
- [Why Your AI Keeps Building the Same Purple Gradient Website（AI-slop 反模式）](https://prg.sh/ramblings/Why-Your-AI-Keeps-Building-the-Same-Purple-Gradient-Website)
- [2026 AI Chat UI Library 评测（Deep Chat / Loquix / assistant-ui）](https://dev.to/alexander_lukashov/i-evaluated-every-ai-chat-ui-library-in-2026-heres-what-i-found-and-what-i-built-4p10)
