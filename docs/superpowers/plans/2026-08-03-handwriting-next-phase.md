# SVG 笔画路径 / AI 手写生成 — 下一阶段路线图

> **文档**: docs/superpowers/plans/2026-08-03-handwriting-next-phase.md
> **基线**: 手写效果子系统已交付（Layer 0: 字体模拟批次）

---

## 概述

当前 Layer 0（字体模拟）已交付，可生成具有手写质感的图片。但存在两个根本限制：
1. **单字形** — 同一个字每次渲染完全相同（YerFont 问题）
2. **非 AI** — 不是真正的"手写"，只是字体 + CSS 扰动

下一阶段分两个方向并进：

---

## Layer 1: SVG 笔画路径（教育场景）— 预计 2-3 周

### 目标
将手写内容转化为逐笔动画的 SVG 路径，支持白板教学、PPT 展示。

### 核心技术
- **SVG Stroke Animation** — 每个汉字拆解为笔画路径，用 `stroke-dasharray` + `stroke-dashoffset` 实现逐笔出现
- **汉字笔画分解** — 利用开源笔画数据库（如 MakeMeAHandwritingFont 的 SVG 数据）或 Canvas 2D 路径追踪
- **Canvas → SVG 转换** — 将已渲染的位图笔画追踪为矢量路径

### 关键组件

| 组件 | 职责 |
|------|------|
| `StrokeDataService` | 笔画数据加载（汉字 Unicode → 笔画 SVG 路径） |
| `SvgAnimator` | 逐笔动画时序控制 + 自定义速度配置 |
| `WhiteboardExporter` | 导出为 SVG / HTML5 Canvas / MP4 动画 |
| `HandwritingAnimationController` | SSE 推送动画渲染进度 |

### 用户场景
1. 教师输入"一元二次方程求根公式" → 生成逐笔手写推导动画
2. 学生选择"楷书"风格 → 看到标准笔画顺序的板书动画
3. 导出为 SVG 嵌入课件/PPT

### 依赖调研
- **汉字笔画数据**: [MakeMeAHandwritingFont](https://github.com/) 的 SVG 笔画集
- **SVG 动画库**: [vivus.js](https://github.com/maxwellito/vivus) / 自研轻量方案
- **Canvas 路径追踪**: `potrace` 或自研位图→矢量转换

---

## Layer 2-3: AI 手写生成 + 个人克隆（内容生产）— 预计 4-6 周

### 目标
基于 LSTM/Diffusion 模型生成真实笔迹，支持个人笔迹克隆。

### 核心技术路线

```
Phase 1: AI 手写增强（第 4-5 周）
  └─ 引入 One-DM 风格迁移
  └─ 输入：文字内容 + 参考风格（现有字体）
  └─ 输出：具有真实笔迹质感的图片

Phase 2: 个人笔迹克隆（第 6-8 周）
  └─ 上传 1-3 张手写样本
  └─ One-DM style encoder 提取笔迹特征
  └─ 生成用户专属笔迹的新文字
```

### 关键组件

| 组件 | 职责 |
|------|------|
| `HandwritingAIModel` | 封装 One-DM / DiffusionPen 模型推理 |
| `StyleEncoder` | 从参考图片提取笔迹风格特征 |
| `PersonalFontService` | 个人笔迹管理（上传/训练/存储/推理） |
| `AIDetoxFilter` | AI 生成内容质量检测（防反AI味） |

### 基础设施需求

| 资源 | 用途 | 预估成本 |
|------|------|---------|
| GPU 推理服务 | 部署 One-DM / Diffusion 模型 | ¥500-2000/月 |
| 模型存储 | 预训练模型 + 用户风格编码 | 10GB+ |
| 推理缓存 | 重复内容加速 | Redis |

---

## 技术选型对比

| 方向 | 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|------|--------|
| SVG 笔画 | 开源笔画数据库 + vivus.js | 成熟、社区数据多 | 中文笔画数据不够完整 | ⭐⭐⭐⭐ |
| SVG 笔画 | 自研 Canvas 路径追踪 | 完全可控 | 精度依赖位图质量 | ⭐⭐⭐ |
| AI 手写 | One-DM (Diffusion) | 单样本即可克隆 | 推理慢、GPU 依赖 | ⭐⭐⭐⭐⭐ |
| AI 手写 | DiffusionPen | 可控风格 | 社区较新、文档少 | ⭐⭐⭐⭐ |
| AI 手写 | 手写 LSTM 传统方案 | 推理快、轻量 | 效果不如 Diffusion | ⭐⭐⭐ |

---

## 第二阶段建议：优先 Layer 1（SVG 笔画路径）

**理由**:
1. 与现有"考研数学白板"场景高度契合，可直接落地
2. 无需 GPU 推理，成本可控
3. Layer 1 的 SVG 笔画数据可作为 Layer 2 的训练数据
4. 用户反馈的收集周期可以为 Layer 2 的模型选型提供方向

**时间线**:
```
第 1 周: 笔画数据调研 + 关键技术验证
第 2 周: SVG 动画渲染引擎开发
第 3 周: 前端白板组件 + 导出功能 + 集成测试
```

---

## 决策清单

| 问题 | 选项 | 建议 |
|------|------|------|
| 先做 AI 还是先做 SVG？ | AI (Layer 2) / SVG (Layer 1) | **SVG 优先** |
| AI 模型选型 | One-DM / DiffusionPen / LSTM | **One-DM**（单样本克隆） |
| GPU 方案 | 自建 / 云 API / 暂不接入 | **暂不接入**，Layer 1 完成后评估 |
| 笔画数据 | 开源 / 自建 / 混合 | **开源优先**，自建补缺 |
| 教育场景 | 单独出产品 / 集成到现有平台 | **集成到现有平台**，复用 skill 引擎 |
| 是否开新分支 | 继续 dev / 新 track 分支 | 创建 `track/handwriting-layer1` |