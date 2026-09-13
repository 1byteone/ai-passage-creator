# Humanizer-zh 集成交付说明

## 1. 交付结论

已将 `Humanizer-zh` 的公开编辑原则适配到文章生成质量链路，用于提升文字的自然度、清晰度、可信度和信息保真度。

本项目不运行外部仓库代码，也不把“降低 AI 检测率”作为产品承诺。`ai-detox` 是兼容现有 Skill 引擎的内容质量审计与改写能力。

## 2. 来源与许可证

| 项目 | 内容 |
| --- | --- |
| 来源仓库 | https://github.com/op7418/Humanizer-zh |
| 固定提交 | `91f3d394db8419c20d67ebe22a96cf8fee0a404b` |
| 许可证 | MIT |
| 适配方式 | 仅吸收公开编辑原则与规则，结合本项目现有 `AntiAiFlavorRules`、`ArticleQualityGateService` 和 Skill Prompt 重写 |

## 3. 运行链路

1. 正文生成 Prompt 自动附加 Humanizer-zh 质量标准。
2. 文章生成完成后，`ArticleQualityGateService` 使用配置阈值进行初检。
3. 初检不通过且开启自动优化时，同步执行 `ai-detox` Skill。
4. 改写结果写回文章状态，并使用同一规则集二次检测。
5. `QUALITY_CHECKED` 事件和 `article_quality` 记录使用最终正文、最终分数和规则版本。

默认配置：

```yaml
article:
  quality-gate:
    enabled: true
    auto-detox: true
    detox-intensity: medium
    pass-threshold: 50
```

## 4. 质量边界

- 不虚构经历、数字、案例、引用、来源或事实。
- 不强制每篇文章加入第一人称、口语词、感叹句或“不完美”表达。
- 保留原文事实、限定条件、专有名词和 Markdown 结构。
- 对模糊归因、空泛结论、宣传式修饰、机械排比和过密格式进行提示或改写。
- 规则命中是编辑提示，不等同于作者身份判断，也不等同于事实核验。
- 改写失败、产出为空或产出与原文相同，都保留原文并记录质量结果。

## 5. 变更范围

- `AntiAiFlavorRules`：增加 Humanizer-zh 来源版本和内容质量规则。
- `AntiAiFlavorChecker`：支持按配置阈值判定。
- `MethodologyPromptAssembler`、`ContentGeneratorAgent`：在正文生成阶段注入质量标准。
- `ArticleQualityGateService`：初检与复检统一使用阈值；持久化最终正文快照和规则版本。
- `ai-detox` Skill：更新描述、来源元数据和两阶段 Prompt。

## 6. 验证命令

```powershell
mvn -Dtest=AntiAiFlavorCheckerTest,ArticleQualityGateServiceTest,SkillEngineIntegrationTest test
mvn test
cd frontend
npm run type-check
npm run build-only
```

交付前还应执行 `git diff --check`，确认没有密钥、Cookie、临时日志或生成物进入提交。
