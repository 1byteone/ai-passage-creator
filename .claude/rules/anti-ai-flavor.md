# Anti-AI-Flavor — 去除 AI 味代码规范

> **加载条件**: 始终加载。基于 [vibe-hub.org/anti-ai-flavor](https://vibe-hub.org/anti-ai-flavor) + [vibe-coding-ai-rules](https://github.com/obviousworks/vibe-coding-ai-rules) + [10 Anti-Patterns](https://www.digitalapplied.com/blog/vibe-coding-anti-patterns-10-ways-it-becomes-tech-debt-2026)

---

## 核心理念

### Trust Spectrum — 根据关键性分配审查力度

```
低信任度 ←——————————————————————————→ 高信任度
├────────────┼──────────────┼──────────────┼─────────────┤
安全关键代码   算法核心        业务逻辑        样板代码
(手动审查)    (单元测试+基准)   (集成测试)      (自动测试+快速扫视)
```

| 代码类型 | 审查强度 | AI 可自主范围 |
|---------|---------|-------------|
| 🔴 安全关键 (认证/授权/加密/XSS) | 手动审查 + SAST | 仅建议，实施后必审 |
| 🟠 算法核心 (计费/排序/匹配) | 单元测试 + 性能基准 + 同行审查 | 可实施，必有测试 |
| 🟡 业务逻辑 (状态机/工作流/校验) | 集成测试 + 相关方审查 | 可实施，需测试覆盖 |
| 🟢 样板代码 (CRUD/配置/格式化) | 自动化测试 + 快速扫视 | 高自主，人工抽查 10% |

### 四大核心原则

| 原则 | 含义 | AI 行为要求 |
|------|------|-----------|
| **Clarify Before Coding** | 先理解需求再写代码 | 意图不清时主动提问，禁止盲目实施 |
| **Simplicity First** | 选择最简单可行的方案 | 复杂模式需明确理由。可读 > 精巧 |
| **Security By Default** | 默认安全 | 校验所有输入。无硬编码密钥。纵深防御。最小权限 |
| **Test-Driven Thinking** | 从测试角度思考设计 | 代码必须可测试。写代码同时写测试。验证后提交 |

---

## AI 代码气味检测 (AI Code Smells)

### 1. 过度工程化 (Over-Engineering)

AI 的默认倾向：为简单需求构建过度抽象。

```java
// ❌ AI 味 — 不必要的抽象层
public interface IUserNameFormatter {
    String format(UserNameDto dto);
}
public class UserNameFormatterImpl implements IUserNameFormatter {
    @Override
    public String format(UserNameDto dto) { return dto.getName(); }
}

// ✅ 人味 — 直接简单
public String getUserDisplayName(User user) {
    return user.getName();
}
```

**规则**: 当只有 1 个实现类时，不需要 Interface+Impl 分离。等出现第 2 个实现时再抽。

### 2. 注释解释 "做了什么" 而非 "为什么"

```java
// ❌ AI 味 — 描述代码本身（多此一举）
// 遍历用户列表，找到 ID 匹配的用户
for (User user : users) {
    if (user.getId().equals(targetId)) { return user; }
}

// ✅ 人味 — 解释为什么需要这个逻辑
// ID 匹配使用 equals() 而非 ==，因为 userId 来自外部输入，可能为 null
for (User user : users) {
    if (user.getId().equals(targetId)) { return user; }
}
```

**规则**: 注释只写 "为什么这样做" 和 "不这样做会怎样"。不写 "代码做了什么"（代码本身已经说了）。

### 3. 变量命名过度描述

```typescript
// ❌ AI 味 — 冗长的描述性命名
const arrayOfFilteredArticlesThatHaveBeenPublished = ...
const temporaryStringBuilderForConstructingErrorMessage = ...

// ✅ 人味 — 领域简洁命名
const publishedArticles = ...
const errorMsg = ...
```

**规则**: 变量名 ≤3 个单词。超过则说明需要拆分或引入类型别名。

### 4. 冗余防御检查

```typescript
// ❌ AI 味 — 层层防御，实际没必要
if (data !== null && data !== undefined && typeof data === 'object'
    && data.items !== null && data.items !== undefined
    && Array.isArray(data.items) && data.items.length > 0) { ... }

// ✅ 人味 — 可选链 + 适量防御
if (data?.items?.length) { ... }
```

**规则**: 使用语言内置的空安全特性 (Optional chaining / `Optional<T>` / `@NonNull`)。不重复检查已保证的条件。

### 5. 错误消息过于正式/翻译腔

```typescript
// ❌ AI 味 — 英文直译风格
"An unexpected error occurred while processing your request. Please try again later."

// ✅ 人味 — 中文直接表达
"操作失败，请稍后重试"
```

```java
// ❌ AI 味
throw new RuntimeException("Failed to retrieve user information from the database");

// ✅ 人味
throw new ServiceException("用户信息查询失败");
```

**规则**: 面向用户的错误消息用中文，简洁直接。日志用英文，带上下文。

### 6. 不必要的 try-catch 包裹

```typescript
// ❌ AI 味 — 每次调用都包一层
try {
  const result = await someSafeOperation()
  return result
} catch (e) {
  console.error('操作失败', e)
  throw e  // 重新抛出，等于没处理
}

// ✅ 人味 — 只在有意义的地方 catch
const result = await someSafeOperation()
return result
// 让全局错误处理器统一处理
```

**规则**: 只在你确实能恢复或需要转换错误类型时才 catch。不要让 catch 块只是 log + rethrow。

### 7. Vue 组件过度拆分

```vue
<!-- ❌ AI 味 — 3行内容拆成组件 -->
<template>
  <div class="greeting">你好，{{ name }}</div>
</template>

<!-- ✅ 人味 — 保持在一起直到真正需要复用 -->
<template>
  <div class="user-section">
    <div class="greeting">你好，{{ name }}</div>
    <!-- 其他紧密相关的 UI -->
  </div>
</template>
```

**规则**: 组件 < 30 行且只在一处使用时，不拆分。

---

## 10 大 Vibe-Coding 反模式 & 防护门

| # | 反模式 | 严重度 | 诊断信号 | 防护门 |
|---|--------|--------|---------|--------|
| 1 | **Accept-Without-Read** — 不读就接受 AI 代码 | 🔴 S1 | 作者无法回答 diff 中的 3 个具体问题 | AI 代码必须标注 + 作者写一行 human summary |
| 2 | **Copy-Paste Sprawl** — AI 无记忆导致重复实现 | 🔴 S1 | 同功能多份实现，半年翻倍 | "先搜索再写" 规则 + 代码去重审计 |
| 3 | **Prompt-Secret Leak** — 密钥泄漏到 prompt | 🔴 S1 | `.env` 被发送到 AI API | `.env` 在 workspace 排除 + 出站代理日志 |
| 4 | **Agent-Blame** — 事后归咎于"模型幻觉" | 🟠 S2 | Postmortem 停在"AI 错了" | 追溯到人的决策点 (审查不足/gate 缺失) |
| 5 | **Eval Skip** — AI 写的代码跳过测试 | 🟠 S2 | AI 代码无对应测试覆盖 | 非对称要求: AI 代码必须带测试 |
| 6 | **Coverage Illusion** — 测试"钉死实现"而非"验证契约" | 🟠 S2 | 重构困难 + 测试通不过说明不了问题 | 人工审查断言的相关性，不只关注行覆盖 |
| 7 | **Over-Abstraction** — 不必要的接口/工厂/模式 | 🟡 S3 | 1 个实现类的 Interface | "Simple First" 原则，等第 2 个实现再抽 |
| 8 | **Prompt Pomposity** — Prompt 变得臃肿不可维护 | 🟡 S3 | 单次 prompt 超过 500 tokens | 拆分为规则文件 + 路径作用域懒加载 |
| 9 | **Doc Rot** — AI 写的文档从未更新 | 🟡 S3 | 文档描述与代码行为不一致 | 文档与代码同 PR 提交，reviewer 验证 |
| 10 | **No Human-in-Loop** — 完全自动化合并 | 🟡 S3 | PR 0 人工评论直接 Merge | 所有 PR 至少 1 个人工 Approve |

---

## Prompt 工程模式

### Feature 实现模式

```
1. Context: "我要在 [位置] 实现 [功能]"
2. Requirements: "它应该 [具体行为]"
3. Constraints: "参考 [参考文件] 的写法"
4. Validation: "需要覆盖 [场景] 的测试"
5. Done when: "[可验证的结果]"
```

### Debug 模式

```
Observation: "实际看到 [错误现象]"
Expectation: "正确行为应该是 [预期]"
Context: "相关代码在 [文件路径]"
Investigation: "我已试过 [排查步骤]"
Ask: "下一步该查什么？"
```

### Review 模式 (AI 自查)

在提交前让 AI 自我审查：
```
Review 刚才生成的代码:
1. Security: 所有输入都校验了吗？有 XSS/注入风险吗？
2. Performance: 有 O(n²) 操作吗？不必要的渲染？
3. Error Handling: 边缘情况如何处理？
4. Compliance: 是否符合 AGENTS.md 规则？
列出所有问题并建议改进。
```

### Iteration 模式 (分阶段实施)

```
Phase 1 (10 min): 核心逻辑，happy path only → STOP 让我测试
Phase 2 (10 min): 错误处理 + 边界情况 → STOP 让我测试
Phase 3 (10 min): Loading 态 + 无障碍 + 动画 → 最终测试
不要跨阶段跳步。
```

---

## 日常工作检查清单

### AI 代码提交前
- [ ] 我读过并理解了 diff 中的每一处变更
- [ ] 没有不必要的抽象层 (多态 <2 实现不抽)
- [ ] 注释解释的是 "为什么" 而非 "做了什么"
- [ ] 用户可见的消息是中文，简洁直接
- [ ] 没有冗余的空 try-catch
- [ ] 没有超过 3 个词的变量名
- [ ] 有对应的测试且测试验证的是契约而非实现细节

### 审查 AI 代码时 (Reviewer)
- [ ] 我能举出 diff 中 1 个不同意的地方
- [ ] 如果有安全相关的改动，我逐行看过了
- [ ] 如果 AI 引入了新工具函数，我搜索确认了没有重复实现
- [ ] 文档变更与代码变更在同一 PR
