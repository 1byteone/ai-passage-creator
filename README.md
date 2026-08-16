# AI Passage Creator — AI 智能文章生成平台

AI 驱动的全栈文章创作平台：**选题 → 标题 → 大纲 → 内容生成 → 卡片渲染 → 审批 → 多平台发布**。

## 核心能力

- **多智能体文章创作**：AI 选题、标题候选、大纲编排、正文生成、配图生成（NANO_BANANA / Agnes / Pexels 三级降级）
- **质量闭环**：anti-AI-flavor 规则检测 + ai-detox 自动改写 + VIP 爆款评分
- **Skill 技能引擎**：14 个可复用 AI 技能（选题/审校/降AI味/种草文案/翻译/SEO/漫画手帐…）+ 链式编排 + HITL 人工确认
- **内容管理**：审批工作流、多平台发布排期（微信/抖音/小红书）、卡片渲染（Playwright HTML→PNG）
- **漫画手帐**：内容→分镜→插画→排版→HTML 渲染落库，`/comic` 浏览 + PNG 下载
- **平台化**：API Key 认证、团队协作空间、管理后台（用户/统计/熔断器/Webhook）

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.5 + Java 21 |
| ORM | MyBatis-Flex 1.11.1 |
| 数据库 | MySQL 8.0（生产）/ H2（测试） |
| 缓存/Session | Redis + Spring Session |
| AI | Spring AI Alibaba（DashScope/Qwen）+ OpenAI Starter（Agnes） |
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue 4 |
| 渲染 | Playwright（Java）HTML→PNG + Mermaid CLI |
| 支付 | Stripe |

## 快速开始

- **生产部署**（docker compose 一键启动）→ 见 [DEPLOYMENT.md](DEPLOYMENT.md)
- **本地开发** → 后端 `mvn spring-boot:run`（端口 8567, context `/api`），前端 `cd frontend && npm run dev`
- **开发规范 / 质量闸门** → 见 [CLAUDE.md](CLAUDE.md)

## 文档索引

- [DEPLOYMENT.md](DEPLOYMENT.md) — docker compose 生产部署、环境变量、常见问题
- [CLAUDE.md](CLAUDE.md) — 工程原则、架构约定、开发工作流、已知暗坑
