# Skill 前端交互组件设计文档

> 基于 Skill 引擎后端 API 的前端交互界面设计
> 日期：2026-07-24
> 项目：AI Passage Creator（灵犀写作）— 前端扩展

---

## 目录

1. [设计目标与原则](#1-设计目标与原则)
2. [整体架构](#2-整体架构)
3. [路由设计](#3-路由设计)
4. [API 对接](#4-api-对接)
5. [SSE 消息处理扩展](#5-sse-消息处理扩展)
6. [组件设计](#6-组件设计)
7. [Skill 输入表单动态渲染](#7-skill-输入表单动态渲染)
8. [创作流程嵌入](#8-创作流程嵌入)
9. [错误处理与边界情况](#9-错误处理与边界情况)
10. [测试策略](#10-测试策略)

---

## 1. 设计目标与原则

### 核心目标

为 Skill 引擎的 3 个首批 Skill（proofreading、topic-gen、article-to-x）提供完整的 Vue 前端交互界面，包括：
- 独立 Skill 中心入口（导航栏 + 首页）
- 通用 Skill 执行流程页面
- 每个 Skill 的定制化结果展示组件
- 在文章创作流程中嵌入常用 Skill 的快捷入口

### 设计原则

| 原则 | 说明 |
|------|------|
| **组件化** | 与现有 `pages/article/components/` 模式一致，单一职责拆分 |
| **复用现有工具** | 复用 `sse.ts`、`permission.ts`、`markdown.ts`、`date.ts` |
| **动态渲染** | 输入表单根据后端 `skill.yaml` 的 variables 定义动态生成 |
| **遵循主题** | 使用现有 CSS 变量（绿色主题）、毛玻璃效果 |
| **渐进增强** | 首批 3 个 Skill，后续新增 Skill 只需新增结果组件 |

---

## 2. 整体架构

```
┌──────────────────────────────────────────────────────────────┐
│  导航栏 GlobalHeader.vue                                     │
│  新增 "技能" 菜单项 → /skill                                  │
└──────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┴─────────────────────┐
        ▼                                           ▼
┌──────────────────┐                    ┌──────────────────────┐
│  首页 HomePage   │                    │  Skill 中心          │
│  新增「AI 工具箱」│                    │  SkillCenterPage.vue │
│  区域（3个卡片）  │                    │  - 卡片网格展示      │
└──────────────────┘                    │  - 分类筛选          │
                                        │  - 点击进入执行页    │
                                        └──────────┬───────────┘
                                                   │
                                                   ▼
                              ┌────────────────────────────────────┐
                              │  Skill 执行页                     │
                              │  SkillExecutePage.vue             │
                              │  - 动态输入表单                   │
                              │  - 执行 + SSE 进度                │
                              │  - 调用定制结果组件               │
                              └─────────────┬──────────────────────┘
                                            │
                       ┌────────────────────┼────────────────────┐
                       ▼                    ▼                    ▼
              ┌──────────────┐    ┌──────────────┐    ┌──────────────────┐
              │ proofreading│    │  topic-gen   │    │  article-to-x   │
              │ 结果组件     │    │  结果组件     │    │  结果组件        │
              │ 对比 diff   │    │  选题选择     │    │  原文 vs 浓缩    │
              └──────────────┘    └──────────────┘    └──────────────────┘

                              ┌──────────────────┐
                              │ SkillLauncher   │
                              │ .vue             │ ← 嵌入创作流程
                              │ 快捷按钮组件     │
                              └──────────────────┘
```

---

## 3. 路由设计

在 `frontend/src/router/index.ts` 新增路由：

| 路径 | 组件 | 导入方式 | 说明 |
|------|------|---------|------|
| `/skill` | `SkillCenterPage.vue` | 动态 import | Skill 中心列表页 |
| `/skill/:skillName` | `SkillExecutePage.vue` | 动态 import | Skill 执行页（通用） |

**路由守卫**：`/skill` 和 `/skill/:skillName` 需要登录态校验（复用 `access.ts` 的现有机制）。

---

## 4. API 对接

### 4.1 新增 API 文件

创建 `frontend/src/api/skillController.ts`，定义 4 个 API 函数：

| 函数 | 后端端点 | 说明 |
|------|---------|------|
| `listSkills()` | `GET /api/skill/list` | 获取所有可用 Skill |
| `getSkillDefinition(skillName)` | `GET /api/skill/{skillName}/definition` | 获取 Skill 定义（含 variables） |
| `executeSkill(skillName, inputs)` | `POST /api/skill/{skillName}/execute` | 执行 Skill，返回 executionId |
| `getSkillResult(executionId)` | `GET /api/skill/{executionId}/result` | 获取执行结果（轮询备用） |

### 4.2 类型定义

在 `frontend/src/api/typings.d.ts` 的 `declare namespace API` 中新增：

```typescript
namespace API {
  // Skill 列表项
  interface SkillListItem {
    name: string
    description: string
    category: string
    phases: number
    multiRound: boolean
  }

  // Skill 变量定义
  interface SkillVariableDef {
    name: string
    description: string
    required: boolean
    source: string
  }

  // Skill 定义详情
  interface SkillDefinition {
    name: string
    description: string
    category: string
    requiredRoles: string[]
    multiRound: boolean
    variables: Record<string, SkillVariableDef>
    phases: SkillPhase[]
  }

  // Skill 阶段
  interface SkillPhase {
    name: string
    promptFile: string
    model: string
    streaming: boolean
    outputParser: string
    outputKey: string
    requireConfirmation: boolean
  }

  // 执行响应
  interface SkillExecuteResponse {
    skillExecutionId: string
    skillName: string
    status: string
    totalPhases: number
    progressUrl: string
  }

  // 结果查询响应
  interface SkillResultVO {
    status: string
    skillName?: string
    phase?: string
    durationMs?: number
    errorMessage?: string
  }
}
```

---

## 5. SSE 消息处理扩展

### 5.1 扩展 sse.ts

现有 `connectSSE` 绑定 `/api/article/progress/{taskId}`。新增 Skill 专用的 SSE 连接函数：

```typescript
// frontend/src/utils/sse.ts 新增

export interface SkillSSEMessage {
  type: string              // skill.started / skill.progress / skill.phase_complete / skill.complete / skill.error
  skillExecutionId?: string
  skillName?: string
  phase?: string
  phaseIndex?: number
  totalPhases?: number
  status?: string
  data?: any                // STREAMING 内容 / 阶段输出
  errorMessage?: string
}

export const connectSkillSSE = (
  executionId: string,
  options: SSEOptions
): EventSource => {
  const { onMessage, onError, onComplete } = options
  const eventSource = new EventSource(`/api/skill/${executionId}/progress`)

  eventSource.onmessage = (event) => {
    try {
      // 后端推送的是 JSON 字符串，如 {"type":"skill.started",...}
      const message: SkillSSEMessage = JSON.parse(event.data)
      onMessage(message)

      if (message.type === 'skill.complete' || message.type === 'skill.error') {
        eventSource.close()
        onComplete?.()
      }
    } catch (error) {
      console.error('Skill SSE 消息解析失败:', error)
    }
  }

  eventSource.onerror = (error) => {
    console.error('Skill SSE 连接错误:', error)
    onError?.(error)
    eventSource.close()
  }

  return eventSource
}
```

### 5.2 消息类型处理映射

| 后端 type | 前端行为 |
|-----------|---------|
| `skill.started` | 显示进度条，阶段 1/N |
| `skill.progress` (含 `STREAMING:` 前缀) | 流式追加内容到当前阶段输出区 |
| `skill.phase_complete` | 进度条推进到下一阶段 |
| `skill.complete` | 关闭 SSE，调用结果组件渲染 |
| `skill.error` | 显示错误提示，关闭 SSE |

---

## 6. 组件设计

### 6.1 SkillCenterPage.vue

**职责**：展示所有可用 Skill 的卡片网格

**接口**：
- 调用 `listSkills()` 获取列表
- 分类筛选（writing / research）
- 点击卡片跳转 `/skill/{name}`

**布局**：
```
┌─────────────────────────────────────────┐
│  AI 技能中心                            │
│  [全部] [写作] [调研]    ← 分类筛选    │
├─────────────────────────────────────────┤
│  ┌────────┐ ┌────────┐ ┌────────┐     │
│  │审校    │ │选题    │ │转社交  │     │
│  │proof.. │ │topic.. │ │artic.. │     │
│  │3阶段   │ │1阶段   │ │1阶段   │     │
│  │[使用] │ │[使用] │ │[使用] │     │
│  └────────┘ └────────┘ └────────┘     │
└─────────────────────────────────────────┘
```

**Props**: 无（自取数据）
**Emits**: 无（路由跳转）

### 6.2 SkillExecutePage.vue

**职责**：通用 Skill 执行流程页面

**Props**: 无（从路由参数 `:skillName` 获取）

**状态机**：
```
INPUT → EXECUTING → COMPLETED
  │         │          │
  │         │          └── 渲染对应结果组件
  │         └── SSE 进度展示
  └── 动态输入表单
```

**流程**：
1. `onMounted` 调用 `getSkillDefinition(skillName)` 获取定义
2. 根据 `variables` 动态渲染输入表单
3. 用户填写后点击「执行」→ `executeSkill(skillName, inputs)`
4. 获取 `skillExecutionId` → 建立 SSE 连接
5. 展示进度条 + 流式内容
6. 收到 `skill.complete` → 切换到结果组件

**结果组件动态加载**：
```typescript
const resultComponents = {
  proofreading: SkillResultProofreading,
  'topic-gen': SkillResultTopicGen,
  'article-to-x': SkillResultArticleToX,
}
// 未知 skill 使用默认 SkillResultDefault.vue（纯文本展示）
```

### 6.3 SkillResultProofreading.vue

**职责**：展示三遍审校结果

**Props**: `resultData: any`（后端返回的 finalContent + reviewResult）

**布局**：
```
┌─────────────────────────────────────┐
│ 审校完成 ✅  评分: 85              │
├─────────────────────────────────────┤
│ 第一遍：内容审校                    │
│  - 逻辑问题: [...]                  │
│  - 结构问题: [...]                  │
├─────────────────────────────────────┤
│ 第二遍：降AI味改写                  │
│  [原文]  vs  [改写后]   ← 并排对比  │
├─────────────────────────────────────┤
│ 第三遍：节奏打磨                     │
│  最终版本: [Markdown 渲染]           │
│  [复制] [导出 Markdown]              │
└─────────────────────────────────────┘
```

### 6.4 SkillResultTopicGen.vue

**职责**：展示选题方案列表，支持选择

**Props**: `resultData: any`（后端返回的 topicOptions 数组）

**布局**：选题卡片网格，每张卡片展示标题、类型、工作量、大纲、优劣分析，点击「使用此选题」触发 emit。

**Emits**: `select(topic)` — 嵌入创作流程时使用。

### 6.5 SkillResultArticleToX.vue

**职责**：展示长文转社交媒体结果

**Props**: `resultData: any`（condensedContent）

**布局**：左侧原文摘要，右侧浓缩版文案，底部「复制」「重新生成」按钮。

### 6.6 SkillLauncher.vue

**职责**：嵌入创作流程的快捷按钮组件

**Props**:
```typescript
interface Props {
  skillName: string          // 要调用的 skill
  buttonText: string         // 按钮文案
  contextData?: Record<string, any>  // 上下文数据（如文章内容）
  icon?: any                 // 按钮图标
}
```

**Emits**: `launched(executionId)` — 启动后通知父组件。

**行为**：点击后弹出 Modal，内嵌 `SkillExecutePage` 的精简版（预设输入、隐藏输入表单）。完成后将结果回传父组件。

---

## 7. Skill 输入表单动态渲染

`SkillExecutePage.vue` 根据 `getSkillDefinition` 返回的 `variables` 动态生成表单：

```typescript
// 变量类型推断（基于 description 或变量名）
function inferFieldType(varDef: API.SkillVariableDef): string {
  const name = varDef.name.toLowerCase()
  if (name.includes('content') || name.includes('article')) return 'textarea'
  if (name.includes('style')) return 'radio'
  if (name.includes('platform')) return 'select'
  return 'input'
}
```

**字段类型映射**：

| 变量特征 | 渲染组件 |
|---------|---------|
| name 含 `content`/`article` | `a-textarea`（多行） |
| name 含 `style` | `a-radio-group`（科技/情感/教育/幽默） |
| name 含 `platform` | `a-select`（微博/小红书/推特） |
| name 含 `direction`/`topic` | `a-input` |
| 其他 | `a-input` |

**required 校验**：`varDef.required === true` 的字段必填。

---

## 8. 创作流程嵌入

在现有创作流程组件中嵌入 `SkillLauncher`：

| 嵌入位置 | 父组件 | 调用 Skill | 按钮文案 |
|---------|--------|-----------|---------|
| 内容生成完成 | `CompletedState.vue` | `proofreading` | 「AI 审校降味」 |
| 文章详情页 | `ArticleDetailPage.vue` | `article-to-x` | 「转社交媒体」 |

**数据传递**：
- `CompletedState` 将 `fullContent` 作为 `contextData.articleContent` 传入
- `SkillLauncher` 启动后预填表单，用户确认即可执行
- 执行完成后，结果可通过 emit 回传父组件，或用户手动复制

---

## 9. 错误处理与边界情况

### 9.1 错误处理

| 场景 | 处理 |
|------|------|
| Skill 不存在（404） | 跳转回 `/skill` 并提示「Skill 不存在」 |
| 未登录调用 | `access.ts` 拦截，跳转登录页 |
| 权限不足（NO_AUTH_ERROR） | `message.error` 提示所需角色 |
| SSE 连接失败 | 重试 1 次，失败后提示「连接中断」 |
| Skill 执行失败（skill.error） | 展示 errorMessage，提供「重试」按钮 |
| 结果为空 | 显示「暂无结果」占位 |

### 9.2 边界情况

- **多轮 Skill（topic-gen）**：首批简化为单轮执行，多轮交互在后续迭代完善（confirm 端点已留接口）
- **SSE 超时**：10 分钟超时后自动关闭，提示用户查看结果
- **浏览器刷新**：执行中刷新页面会丢失 SSE，通过 `getSkillResult(executionId)` 轮询恢复状态
- **并发执行**：同一用户可并发执行多个 Skill，每个有独立 executionId

---

## 10. 测试策略

### 10.1 手动验证清单

| 测试项 | 验证点 |
|--------|-------|
| Skill 中心列表加载 | 调用 `/skill/list` 返回 3 个 Skill |
| 输入表单动态渲染 | proofreading 显示 textarea + radio |
| 执行 + SSE 进度 | 进度条推进，流式内容追加 |
| 审校结果对比 | 原文 vs 改写并排展示 |
| 选题选择交互 | 卡片点击高亮，emit 事件 |
| 创作流程嵌入 | CompletedState 显示「审校」按钮 |
| 错误提示 | 未登录/权限不足/执行失败 |
| 浏览器刷新恢复 | 执行中刷新后能查询状态 |

### 10.2 关键测试场景

1. **端到端 proofreading 流程**：输入文章 → 执行 → SSE 接收 → 对比结果展示
2. **topic-gen 选择回传**：选题 → 点击使用 → emit 到父组件
3. **article-to-x 嵌入**：文章完成后点击「转社交媒体」→ Modal 弹出 → 预填文章 → 执行 → 复制结果

---

## 附录：新增文件清单

| 文件 | 类型 | 所属 |
|------|------|------|
| `frontend/src/api/skillController.ts` | API | 新增 |
| `frontend/src/pages/skill/SkillCenterPage.vue` | 页面 | 新增 |
| `frontend/src/pages/skill/SkillExecutePage.vue` | 页面 | 新增 |
| `frontend/src/pages/skill/components/SkillResultProofreading.vue` | 组件 | 新增 |
| `frontend/src/pages/skill/components/SkillResultTopicGen.vue` | 组件 | 新增 |
| `frontend/src/pages/skill/components/SkillResultArticleToX.vue` | 组件 | 新增 |
| `frontend/src/pages/skill/components/SkillResultDefault.vue` | 组件 | 新增 |
| `frontend/src/components/SkillLauncher.vue` | 组件 | 新增 |
| `frontend/src/router/index.ts` | 修改 | 新增 2 路由 |
| `frontend/src/utils/sse.ts` | 修改 | 新增 connectSkillSSE |
| `frontend/src/api/typings.d.ts` | 修改 | 新增 Skill 类型 |
| `frontend/src/components/GlobalHeader.vue` | 修改 | 新增菜单项 |
| `frontend/src/pages/HomePage.vue` | 修改 | 新增工具箱区域 |
| `frontend/src/pages/article/components/CompletedState.vue` | 修改 | 嵌入审校按钮 |
| `frontend/src/pages/article/ArticleDetailPage.vue` | 修改 | 嵌入转社交按钮 |

---

> **维护者**: YangJs
> **项目**: AI Passage Creator（灵犀写作）— Skill 前端扩展