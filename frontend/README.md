# 前端项目

基于 Vue 3 + Vite + Ant Design Vue 的前端项目模板。

## 环境要求

- Node.js >=22.19.0（Lighthouse 13.4.1 的最低要求）

## 技术栈

- Vue 3
- Vite
- TypeScript
- Ant Design Vue
- Axios
- Pinia
- Vue Router

## 开发

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 代码格式化
npm run format

# 代码检查
npm run lint
```

## 测试

前端状态测试可以独立运行：

```bash
npm run test:skill
```

核心路由 UI 回归需要 Chrome，以及运行在 `http://127.0.0.1:8567` 的 `local` 或 `test`
Profile 后端。测试命令会自动构建并启动独立 Vite 生产预览（不复用旧服务），并使用本地
管理员测试账号验证受保护路由：

```bash
# 在项目根目录启动本地后端
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 在 frontend 目录运行全部前端测试
npm test

# 只运行 Playwright UI 回归
npm run test:ui

# 独立生产预览下运行单次 Lighthouse 冒烟采样（预算超限仅提示）
npm run test:performance

# 发布前稳定硬门禁：预热后对每个路由正式采样 3 次
npm run test:performance:release

# 串行执行 Lint、生产构建、体积预算、UI 与 release 性能门禁
npm run check
```

可通过 `UI_TEST_BASE_URL` 指向已运行的前端，通过 `UI_TEST_ADMIN_ACCOUNT` 和
`UI_TEST_ADMIN_PASSWORD` 覆盖测试账号；非 Chrome 环境可使用
`PLAYWRIGHT_BROWSER_CHANNEL` 指定 Playwright 浏览器通道。

性能采样要求先生成最新 `dist`，并要求 `http://127.0.0.1:8567` 的后端可用。脚本默认
动态选择空闲端口启动独立 Vite 生产预览，且会核对预览 HTML 的入口哈希与当前
`dist/index.html` 一致，避免误采旧服务。首页、登录页和 Skill 中心会先经过一次预热导航，
校验 H1、关键内容、关键 API 状态及运行时错误，再开始正式 Lighthouse 采样。

`test:performance` 是快速 smoke：默认每个路由采样 1 次，性能预算仅观察，但页面或采样
有效性错误仍会阻断。`test:performance:release` 是发布硬门禁：默认每个路由采样 3 次，
Performance、FCP、LCP 与 TBT 使用原始值中位数比较，CLS 使用全部样本最大值比较；
数值只在展示时格式化，不参与门禁计算。

汇总报告写入 `performance-results/latest.json` 和 `performance-results/latest.md`，同时为
每个路由保存 `latest-*.lhr.json` 代表性原始 LHR。报告包含 Node、Lighthouse、Chrome、
节流配置、LCP 元素与页面预热校验记录。Lighthouse 实验室环境不直接提供 INP；TBT
仅是主线程阻塞的实验室代理，不能与真实用户 INP 等同。

可通过 `PERF_BASE_URL` 复用入口哈希与当前 `dist` 一致的已有生产服务器，通过
`PERF_CHROME_PATH` 指定 Chrome，或使用 `PERF_MIN_SCORE`、`PERF_MAX_FCP_MS`、
`PERF_MAX_LCP_MS`、`PERF_MAX_CLS`、`PERF_MAX_TBT_MS` 覆盖默认性能预算。
`PERF_RUNS` 允许设置 1–5 次采样（release 不得少于 3 次），`PERF_PREVIEW_HOST` 和
`PERF_PREVIEW_PORT` 可覆盖预览监听地址；未指定端口时始终动态选择空闲端口。所有数值型
环境变量都会执行有限值和范围校验，非法配置会直接终止门禁。

## 生成 API 代码

```bash
npm run openapi2ts
```

## 目录结构

```
src/
├── api/           # API 接口定义
├── assets/        # 静态资源
├── components/    # 公共组件
├── config/        # 配置文件
├── layouts/       # 布局组件
├── pages/         # 页面组件
├── router/        # 路由配置
├── stores/        # Pinia 状态管理
└── utils/         # 工具函数
```
