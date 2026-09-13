# 生产部署指南

AI Passage Creator 采用 docker compose 一键启动全栈：**MySQL + Redis + 后端 + 前端(nginx)**。

## 1. 前置要求

- Docker + Docker Compose（Compose V2）
- 各第三方服务 API Key（见 `.env.example`）

## 2. 快速启动

```bash
# 1. 复制环境变量模板并填写真实值
cp .env.example .env

# 2. 编辑 .env，至少填必填项：
#    DB_ROOT_PASSWORD  DB_PASSWORD  REDIS_PASSWORD
#    其余业务 key（AI/COS/Pexels/Stripe/LangSearch）按需填写

# 3. 构建并启动
docker compose up -d --build

# 4. 查看状态
docker compose ps
```

启动完成后访问 **http://localhost**（前端），API 在 `http://localhost:8567/api/`。

## 3. 服务端口

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| 前端 | apc-frontend | **80** | nginx，静态资源 + `/api/` 反向代理 |
| 后端 | apc-backend | **8567** | Spring Boot，context-path `/api` |
| MySQL | apc-mysql | 3306 | 库 `ai_passage_creator`，应用用户默认 `appuser` |
| Redis | apc-redis | 6379 | 需密码（`REDIS_PASSWORD`） |

> 仅内部暴露的端口：3306 / 6379。生产环境建议通过安全组限制这些端口仅内网可访问，只对外暴露 80。

## 4. 环境变量

完整变量清单见 [.env.example](.env.example)。关键说明：

| 变量 | 必填 | 说明 |
|------|------|------|
| `DB_ROOT_PASSWORD` / `DB_PASSWORD` / `REDIS_PASSWORD` | ✅ | compose fail-fast，缺失则启动报错 |
| `DASHSCOPE_API_KEY` | 按需 | 文章创作默认模型（Qwen） |
| `AGNES_API_KEY` | 按需 | Agnes AI Skill 引擎默认模型；旧环境可兼容 `AGNES_AI_API_KEY` |
| `LANGSEARCH_API_KEY` | 可选 | research 技能真实联网搜索；不配则退化为模型知识 |
| `TENCENT_COS_*` | 按需 | 卡片/配图/手写笔记存储 |
| `STRIPE_*` / `PEXELS_API_KEY` / `WEBHOOK_SHARED_SECRET` | 按需 | 支付 / 图片 / Webhook |

## 5. 数据与迁移

- **Flyway 自动迁移**：后端首次启动自动执行 `src/main/resources/db/migration/` 下的 SQL，无需手工建表
- **数据卷**：`mysql_data` / `redis_data` 持久化在 Docker volume，`docker compose down` 不删除数据
- **备份建议**：
  ```bash
  docker exec apc-mysql mysqldump -u root -p ai_passage_creator > backup_$(date +%F).sql
  ```

## 6. 健康检查

- 前端：`GET /health`（nginx 返回 200）
- 后端：`GET /api/health/`（容器 HEALTHCHECK，失败自动重启尝试）

## 7. 容器内特殊依赖

后端容器内置了卡片渲染所需依赖：**Playwright Chromium**（HTML→PNG）、**Mermaid CLI**（图表）、**CJK 中文字体**（noto-cjk / wqy-zenhei）。卡片渲染失败时优先检查这些依赖。

## 8. 常见问题（FAQ）

| 现象 | 原因与处理 |
|------|-----------|
| 前端页面打开但 API 全 502 | backend 未就绪或崩了：`docker compose logs backend` 排查；frontend 等待 `depends_on` 健康检查 |
| 登录后 session 很快失效 | Redis 未配置密码导致连不上：核对 `.env` 的 `REDIS_PASSWORD` 与 compose 一致 |
| 文章生成报「LLM 服务不可用」 | `AGNES_API_KEY` / `DASHSCOPE_API_KEY` 未配置或失效 |
| research 技能返回「搜索服务未配置」 | 未配置 `LANGSEARCH_API_KEY`；配置后重启 backend |
| 卡片渲染失败/空白 | 容器内 Playwright 或字体依赖损坏：`docker compose build backend --no-cache` 重建 |
| 中文乱码（PDF/Word 导出） | 字体缺失，重建镜像确保 `font-noto-cjk` / `wqy-zenhei` 已安装 |

## 9. 升级

```bash
git pull
docker compose up -d --build   # Flyway 自动执行新增迁移
```

## 10. research 真实搜索验证

`WebSearchTool` 调用 LangSearch 真实 API（`https://api.langsearch.com/v1/web-search`，OpenAI 兼容格式）。验证方式：

```bash
# 1. 配置 key
export LANGSEARCH_API_KEY=sk-xxxx

# 2. 单元集成验证（mock 服务器，验证请求构造，无需真实 key）
mvn test -Dtest=WebSearchToolHttpTest

# 3. 真实搜索端到端验证（需真实 key + 可访问 api.langsearch.com）
RUN_LANGSEARCH_REAL_TEST=true mvn test -Dtest=LangSearchRealTest
```

**已实测确认**（2026-08）：真实 key + 真实 API 返回 `{"code":200,"data":{"_type":"SearchResponse","webPages":{"value":[...]}}}`，结构化搜索结果正确。注意：若运行环境有 TLS 中间人拦截（如沙箱/代理），Java `HttpClient` 默认校验证书会失败返回「搜索服务暂时不可用」，需在无拦截的生产网络环境验证。

## 11. 生产安全建议

- 修改 MySQL/Redis 默认端口，或限制防火墙只暴露 80
- 通过 HTTPS 反向代理（如 Caddy/Nginx）终止 TLS
- 定期备份 `mysql_data` volume
- 监控 `/api/health/` 与容器健康状态
