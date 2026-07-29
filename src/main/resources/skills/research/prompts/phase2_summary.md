---
phase: summary
model: agnes
outputType: json
---

# 结构化调研 - 总结阶段

## 调研结果（来自实时网络搜索）
{searchResults}

## 要求
1. 根据搜索阶段获取的真实搜索结果，整理成结构化简报
2. 区分已确认事实和待验证信息，标注可信度
3. 引用搜索结果中的来源链接
4. 提供基于真实数据的写作建议
5. 输出 JSON 格式

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