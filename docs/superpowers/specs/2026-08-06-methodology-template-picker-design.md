# 创作方法论文档模板选择器 — 卡片式可视化升级

日期: 2026-08-06
状态: 已对齐

## Context

项目已有 4 个方法论模板（default/douyin/xiaohongshu/wechat），但创作页选择器是**静态单选**（4 个硬编码 radio），不展示模板的描述/适用平台/维度，用户无法理解选择什么。

本功能：**新增后端接口返回模板列表 + 创作页选择器升级为卡片式模板选择器**（每张卡片展示名称、描述、适用平台、字数范围、核心创作维度标签）。

## 方案

### 1. 后端 — 新增 MethodologyController

**新增 `GET /methodology/list`**（登录即可）：
```java
@RestController
@RequestMapping("/methodology")
@Tag(name = "MethodologyController", description = "创作方法论模板")
public class MethodologyController {
    private final MethodologyRegistry methodologyRegistry;

    // GET /methodology/list → 返回 List<MethodologyVO>
    // MethodologyVO: { name, description, version, platformName, audience,
    //                  minChars, maxChars, cardStyle, dimensionKeys }
}
```

**MethodologyVO**（精简版，只返回模板市场需要的字段）：
- `name` — 模板名（default/douyin/xiaohongshu/wechat）
- `description` — 模板描述
- `platformName` — 适用平台名
- `audience` — 目标人群
- `minChars`/`maxChars` — 字数范围
- `cardStyle` — 卡片风格
- `dimensionNames` — 核心创作维度名列表

### 2. 前端 — 创作页模板选择器升级

把静态 radio（L146-151）升级为**卡片式选择器**：
- 从接口 `getMethodologyList()` 加载模板列表
- 每张卡片展示：模板名 + 描述 + 平台徽标 + 字数范围 + 维度标签
- 点击卡片选中（选中高亮，复用 `topic-card-selected` 样式）
- 失败时回退到静态 radio（不阻塞创作）

**API 封装**：`frontend/src/api/methodologyController.ts` 新增 `getMethodologyList()`。

## 数据流

```
创作页 onMounted → GET /methodology/list → 渲染模板卡片列表
用户点击卡片 → selectedMethodology = card.name → createArticle 透传
```

## 文件清单

| 文件 | 操作 |
|------|------|
| `src/main/java/.../controller/MethodologyController.java` | 新增（GET /methodology/list）|
| `src/main/java/.../model/vo/MethodologyVO.java` | 新增（模板精简 VO）|
| `frontend/src/api/methodologyController.ts` | 新增（getMethodologyList）|
| `frontend/src/pages/article/ArticleCreatePage.vue` | 方法论选择器升级为卡片式 |

## 不做的事

- 不做独立模板市场页面（用户确认创作页升级）
- 不改 MethodologyDefinition/Registry（复用现有 getNames/get）
- 不做模板增删改（管理后台后续）

## 验证

```bash
mvn test -Dspring.profiles.active=test   # 后端测试
cd frontend && npm run type-check && npm run build
```
- 创作页加载显示 4 张模板卡片（名称/描述/平台/字数/维度）
- 点击卡片选中，createArticle 透传 methodology
- 接口失败回退静态 radio
