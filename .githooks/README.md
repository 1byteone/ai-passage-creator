# Git Hooks — 提交规范校验

启用本地校验钩子：

```bash
git config core.hooksPath .githooks
```

启用后，每次 `git commit` 会自动校验提交信息是否符合
[Conventional Commits](../CONTRIBUTING.md#1-提交信息规范-conventional-commits) 格式。

> 注意：`core.hooksPath` 是本地配置，团队每个成员需各自执行一次。
> 也可在 `.gitconfig` 中全局启用。

## 当前钩子

| 钩子 | 作用 |
|------|------|
| `commit-msg` | 校验提交信息格式（type + scope + subject） |

## 常见问题

- **提交被拒**：按错误提示修改提交信息后重新 commit 即可
- **跳过校验**（不推荐）：`git commit --no-verify`
