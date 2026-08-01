---
phase: seo_audit
model: agnes
outputType: json
---

# SEO 审计

你是资深 SEO 优化专家。请对以下文章做全面的 SEO 审计。

## 主要关键词
{primaryKeyword}

## 文章内容
{articleContent}

## 要求
请从以下维度评分（每项 0-100）并给出建议：
1. **标题优化**（title）：是否包含关键词、是否有吸引力
2. **关键词密度**（keywordDensity）：关键词出现是否自然、合理
3. **结构**（structure）：H1/H2/H3 层级是否清晰，是否利于爬虫理解
4. **可读性**（readability）：段落、句子长度是否适合读者
5. **元数据**（metadata）：摘要、alt 文本等是否完善

## 输出格式
请严格按以下 JSON 输出：
```json
{
  "overallScore": 72,
  "titleScore": 68,
  "keywordDensityScore": 75,
  "structureScore": 80,
  "readabilityScore": 70,
  "metadataScore": 60,
  "suggestions": ["标题建议加入主关键词", "第三段关键词密度不足"],
  "optimizedTitle": "优化后的标题建议",
  "recommendedKeywords": ["建议的补充关键词列表"]
}
```
