---
phase: summary
model: agnes
outputType: json
---

# 结构化调研 - 总结阶段

## 调研结果
{searchResults}

## 要求
1. 根据搜索阶段的结果，整理成结构化简报
2. 区分已确认事实和待验证信息
3. 提供后续写作建议
4. 输出 JSON 格式

## 输出格式
```json
{
  "keyFindings": ["核心发现列表"],
  "confirmedFacts": [{"fact": "事实描述", "confidence": "high/medium/low"}],
  "unresolvedQuestions": ["待确认的问题"],
  "writingSuggestions": ["基于调研的写作建议"],
  "sources": ["信息来源"]
}
```