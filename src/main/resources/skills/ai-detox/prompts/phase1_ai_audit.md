---
phase: ai_audit
model: agnes
outputType: json
---

# 文章表达质量审计

你是专业中文内容编辑。请审计以下文章的表达质量，识别影响自然度、清晰度、可信度和可读性的具体问题，并给出可执行的优化方向。

本审计用于改进文字质量，不用于判断作者身份，也不以绕过任何检测器为目标。只指出文本中实际存在的问题，不因文章没有第一人称而判定为问题。

## 文章内容
{articleContent}

## 分析维度
1. **句式与段落节奏**（syntaxAndRhythm）：是否存在连续同构句式、机械三段式、段落长度过度均匀
2. **词语准确性**（wordChoice）：是否有空泛、夸大、宣传式修饰，是否能用更具体的动词和名词表达
3. **连接与结构**（cohesion）：连接词是否过密，转折和因果是否真的成立
4. **来源与事实边界**（evidenceBoundary）：是否存在模糊归因、无来源数字、虚构引用或超出原文的信息
5. **表达自然度**（naturalness）：是否有聊天式客套、免责声明、空泛总结或不必要的模板化 Markdown
6. **信息保真**（fidelity）：改写时必须保留的事实、限定条件、专有名词和 Markdown 结构

## 输出格式
严格按以下 JSON 输出：
```json
{
  "overallRisk": 78,
  "syntaxAndRhythmScore": 75,
  "wordChoiceScore": 80,
  "cohesionScore": 70,
  "evidenceBoundaryScore": 85,
  "naturalnessScore": 80,
  "fidelityNotes": ["必须保留原文中的具体日期、产品名称和限定条件"],
  "highRiskPatterns": ["多个段落使用相同的三句结构", "使用了无具体来源的“专家认为”"],
  "optimizationDirections": ["合并重复转折", "把抽象形容词换成具体动作", "删除没有信息增量的总结句"],
  "suggestedReplacements": {
    "此外": "另外 / 还有 / 顺便说一句",
    "综上所述": "直接给出结论",
    "专家认为": "删除模糊归因，或补充具体来源"
  }
}
```
