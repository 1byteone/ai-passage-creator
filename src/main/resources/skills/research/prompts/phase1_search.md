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

## 可用工具
你有一个 `webSearch` 工具，可以执行实时网络搜索。请积极使用它获取真实、最新的信息。

## 要求
1. 针对调研主题和关键问题，使用 webSearch 工具搜索真实信息
2. 每个关键问题至少搜索一次，确保覆盖全面
3. 根据搜索结果，整合提炼出已知信息和需要进一步探索的领域
4. 引用搜索结果的来源
5. 全部完成后输出 JSON 格式

## 输出格式
```json
{
  "topic": "调研主题",
  "knownInfo": ["已知道的信息点（每条注明来源）"],
  "unknownAreas": ["需要进一步探索的领域"],
  "searchQueries": ["实际使用的搜索关键词"],
  "searchedItems": [
    {
      "query": "搜索词",
      "results": ["搜索结果摘要"]
    }
  ],
  "keyQuestions": ["需要回答的问题列表"]
}
```