---
phase: search
model: agnes
outputType: json
---

# 结构化调研 - 搜索阶段

## 调研主题
{topic}

## 关键问题
{questions}

## 要求
1. 围绕调研主题，系统化梳理已知信息和未知领域
2. 针对每个关键问题，列出需要查找的信息点
3. 输出 JSON 格式

## 输出格式
```json
{
  "topic": "调研主题",
  "knownInfo": ["已知道的信息点"],
  "unknownAreas": ["需要进一步探索的领域"],
  "searchQueries": ["建议搜索的关键词"],
  "keyQuestions": ["需要回答的问题列表"]
}
```