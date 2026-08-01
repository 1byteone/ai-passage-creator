---
phase: content_review
model: agnes
outputType: json
---

# 第一遍审校：内容审校

## 目标
确保内容准确、逻辑清晰、结构合理。

## 检查项
对以下文章内容进行审校：
{articleContent}

文章风格：{style}

## 具体要求
1. 检查事实准确性（数据、时间、产品名称）
2. 检查逻辑清晰度（前后无矛盾）
3. 检查结构合理性（无跑题、信息完整）
4. 检查是否有编造内容（所有数据和案例必须真实）

## 输出格式
必须返回 JSON 格式，不要包含其他内容：

```json
{
  "isAccurate": true,
  "logicIssues": ["问题1描述", "问题2描述"],
  "structureIssues": ["结构问题"],
  "suggestions": ["修改建议"],
  "overallScore": 85,
  "summary": "总体评价"
}
```

overallScore 范围 0-100，分数越高表示质量越好。