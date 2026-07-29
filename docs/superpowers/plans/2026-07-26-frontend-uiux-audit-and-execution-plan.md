# AI Passage Creator 前端 UI/UX 全面审计与执行计划

> 日期：2026-07-26
> 模式：Redesign Preserve，保留现有品牌、路由、导航名称和业务流程
> 设计判断：面向内容创作者的 AI 生产力工作台，强调任务连续性、状态清晰、可信表达和高频使用效率
> 设计参数：DESIGN_VARIANCE 5 / MOTION_INTENSITY 3 / VISUAL_DENSITY 6

## 1. 执行摘要

当前前端已经完成首页与创作页的第一轮结构升级，整体方向正确，已从纯营销卡片页向任务型工作台演进。主要风险已经从“页面不好看”转为以下四类系统问题：

1. 认证边界不完整，未登录用户进入创作、历史和详情路由时可能看到空白主区。
2. 页面语义不稳定，存在重复 H1、嵌套 main、placeholder 代替 label 等问题。
3. 页面状态覆盖不均衡，登录和注册失败依赖瞬时 toast，缺少内联恢复信息和提交锁定。
4. 性能债务集中在 Ant Design Vue 与 ECharts vendor chunk，首屏入口已变小，但全站依赖成本仍高。

推荐继续采用渐进式升级，不更换 Vue 3 与 Ant Design Vue，不重做信息架构。先补齐语义、状态和认证，再推进组件系统、后台页和性能治理。

## 2. 研究来源

本轮确认并采用以下 17 个权威来源：

1. [WCAG 2.2 Quick Reference](https://www.w3.org/WAI/WCAG22/quickref/)
2. [WCAG Focus Appearance](https://www.w3.org/WAI/WCAG22/Understanding/focus-appearance.html)
3. [WCAG Target Size Minimum](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html)
4. [web.dev Web Vitals](https://web.dev/articles/vitals)
5. [web.dev Interaction to Next Paint](https://web.dev/articles/inp)
6. [web.dev Cumulative Layout Shift](https://web.dev/articles/cls)
7. [web.dev Accessible Forms](https://web.dev/learn/accessibility/forms)
8. [MDN ARIA Live Regions](https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/ARIA_Live_Regions)
9. [MDN prefers-reduced-motion](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion)
10. [MDN Container Queries](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_containment/Container_queries)
11. [Vue Performance Guide](https://vuejs.org/guide/best-practices/performance)
12. [Vite Production Build Guide](https://vite.dev/guide/build.html)
13. [GOV.UK Error Message](https://design-system.service.gov.uk/components/error-message/)
14. [GOV.UK Notification Banner](https://design-system.service.gov.uk/components/notification-banner/)
15. [Material 3 Accessible Design](https://m3.material.io/foundations/accessible-design/overview)
16. [Apple HIG Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility)
17. [Nielsen Norman Group Usability Heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/)

核心结论：

1. 表单必须有持久可见或程序可识别的 label，placeholder 只用于示例。
2. 错误信息应靠近问题位置，并说明恢复方式。
3. 焦点必须可见，触控目标必须足够大，状态不能只靠颜色表达。
4. 动态进度与结果应使用合适的 aria-live，但不能让频繁日志淹没读屏输出。
5. 性能验收应同时覆盖 LCP、INP、CLS 与 JavaScript 体积，而不是只看构建是否成功。

## 3. 当前评分

| 维度 | 当前分 | 目标分 | 主要判断 |
| --- | ---: | ---: | --- |
| 信息架构 | 78 | 90 | 首页任务链清楚，创作页已开始重排，外围页面仍需统一 |
| 视觉系统 | 76 | 90 | 品牌一致，但硬编码颜色、白色卡片和单一绿色仍较多 |
| 交互反馈 | 68 | 90 | Skill 状态较完整，认证页和部分业务页状态不足 |
| 可访问性 | 55 | 90 | 焦点基础已存在，但标题、landmark、label 和错误恢复有缺口 |
| 响应式 | 72 | 90 | 首页与创作页已有 375/768 策略，后台和认证页仍偏缩放式 |
| 性能 | 58 | 85 | 主入口变小，但 antd 与 echarts chunk 仍超过 1 MB |
| 系统一致性 | 70 | 90 | token v1 已启动，页面仍存在局部自定义规则和重复 CSS |
| 综合 | 68 | 89 | 已具备专业产品雏形，需要系统收口 |

## 4. 实测与代码证据

### 4.1 P0 问题

| 问题 | 证据 | 影响 | 状态 |
| --- | --- | --- | --- |
| 未登录访问创作页出现空白主区 | `/create` 未设置 requiresAuth，组件依赖登录态 | 核心入口失效，用户无法理解下一步 | 本轮已修复 |
| 开发代理与实际后端端口脱节 | Vite 固定 8567，运行实例曾位于 8123 | 页面可打开但所有 API 失败 | 本轮已支持环境变量 |
| 全局品牌标题使用 H1 | GlobalHeader 在每页输出 H1 | 页面出现多个 H1，标题层级混乱 | 本轮已修复 |
| 页面内嵌套 main | BasicLayout 已输出 main，Skill 与创作页再次输出 main | landmark 导航混乱 | 本轮已修复 |
| 登录注册只有 placeholder | 账号、密码控件没有持久 label | 读屏、自动填充和认知可用性下降 | 本轮已修复 |

### 4.2 P1 问题

1. 登录与注册页重复约 200 行品牌区和表单样式，应收敛到 AuthBrandSection 与 AuthFormShell。
2. SkillCenterPage 使用后端 description 优先，导致首页与 Skill 中心文案口径不一致。
3. Skill 卡片固定最小高度 260px，内容少时空白过多，移动端扫描效率一般。
4. 多个页面仍直接使用 `background: white` 和原始 hex，应迁移到 surface 与 semantic status token。
5. ArticleCreatePage 主模板与 InputState 等拆分组件存在重复实现，维护成本高。
6. 后台图表缺少文本摘要、加载失败后的替代数据和可访问表格。
7. 全局 header 使用 backdrop-filter，需验证低性能设备和不支持透明效果时的回退。

### 4.3 P2 问题

1. 登录页品牌文案“10万+”属于强结果承诺，缺少证据时应改为可验证的流程价值。
2. 页面仍偏单一绿色，建议保留绿色主强调，引入蓝色信息状态与琥珀色提醒状态。
3. 首页流程预览仍是代码构成的示意界面，后续可替换为真实产品截图或真实组件预览。
4. 深色模式尚未形成完整 token 映射，不建议在 P0/P1 未完成前启动。

## 5. 页面级优化计划

### 5.1 首页

目标：5 秒内说清产品、目标用户和下一步。

1. 保留左侧任务入口与右侧流程证据布局。
2. 首页输入提供持久 label、帮助信息和登录前预期说明。
3. 登录用户优先显示最近创作，游客优先显示流程证据。
4. 工具箱描述统一使用前端配置文案，避免后端旧描述覆盖品牌口径。
5. 控制首屏文本元素数量，主 CTA 只保留“开始创作”。

### 5.2 创作页

目标：任何阶段都能回答“我在哪、系统在做什么、下一步是什么”。

1. 继续使用主工作区加可折叠辅助区，不恢复永久三栏。
2. 将阶段标题统一为单一 H1，阶段内使用 H2/H3。
3. 输入、标题、大纲、生成、完成五类状态使用统一 status shell。
4. 错误信息包含原因、当前数据是否保留、可执行的恢复动作。
5. 日志默认显示最近关键事件，完整日志放入可展开面板。

### 5.3 Skill 中心与执行页

目标：进入前理解输入与输出，执行中理解进度，结束后方便复用。

1. 卡片文案展示“需要什么”和“得到什么”，不使用泛化能力描述。
2. 统一 SkillInputForm 的 label、helper、required、error 和字符统计。
3. 进度区域限制 aria-live 更新频率，只播报阶段变化与最终结果。
4. 结果组件统一提供复制、下载、继续编辑和重新执行。
5. 未知 Skill 使用安全回退页，并提供返回 Skill 中心入口。

### 5.4 认证页

目标：安静、可信、快速完成，不让品牌装饰压过任务。

1. 主标题改为“欢迎回来”或“创建账号”，品牌名不占用 H1。
2. 表单使用 vertical label，开启 username/current-password/new-password 自动填充。
3. 提交时按钮锁定并显示 loading，失败信息保留在表单上方。
4. 合并登录与注册的重复品牌区和表单样式。
5. 评估移除大面积动态渐变，改为低成本品牌色面。

### 5.5 历史、详情与后台

1. 历史页将筛选器收敛为移动端抽屉或折叠区。
2. 详情页提供稳定的阅读宽度、固定操作区和导出反馈。
3. 统计页为每张图表增加一句文本结论、时间范围、空态和重试动作。
4. 用户管理表在窄屏下优先保留账号、角色和操作，其余信息进入详情抽屉。

## 6. 组件状态矩阵

| 组件 | 默认 | Hover | Focus | Disabled | Loading | Empty | Error | Success |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Primary Button | 必须 | 必须 | 必须 | 必须 | 必须 | 不适用 | 不适用 | 可选 |
| Text Input | 必须 | 必须 | 必须 | 必须 | 不适用 | 必须 | 必须 | 可选 |
| Skill Card | 必须 | 必须 | 必须 | 可选 | 骨架 | 列表空态 | 列表错误 | 不适用 |
| Workflow Panel | 必须 | 可选 | 必须 | 可选 | 骨架/进度 | 初始态 | 可恢复错误 | 完成态 |
| Chart | 必须 | 必须 | 必须 | 不适用 | 骨架 | 无数据说明 | 重试 | 数据摘要 |
| Toast/Alert | 不适用 | 不适用 | 不抢焦点 | 不适用 | 不适用 | 不适用 | aria-live | aria-live |

## 7. 设计系统收口

### 7.1 Token

1. Surface：canvas、surface、surface-muted、surface-raised。
2. Text：primary、secondary、muted、inverse。
3. Border：default、strong、focus。
4. Status：info、success、warning、danger，每类包含 surface/text/border。
5. Motion：fast 120ms、normal 180ms、emphasis 240ms。
6. Radius：输入 8px、卡片 8px、按钮 8px，避免继续扩大圆角层级。

### 7.2 组件

优先沉淀：PageHeader、FormField、InlineAlert、AsyncButton、EmptyState、ErrorState、WorkflowStatus、ChartPanel。

## 8. 性能预算

| 指标 | 目标 |
| --- | --- |
| LCP | 小于 2.5 秒 |
| INP | 小于 200 毫秒 |
| CLS | 小于 0.1 |
| 首屏路由 JS gzip | 小于 200 KB，不含缓存 vendor |
| 单个业务路由 chunk gzip | 小于 120 KB |
| Ant Design vendor | 通过按需组件降至当前的 60% 以下 |
| ECharts vendor | 使用模块化导入，降至当前的 50% 以下 |

实施顺序：

1. Ant Design Vue 从全量注册迁移为按需组件。
2. ECharts 使用 core、charts、components、renderers 模块化导入。
3. 统计页仅在进入路由时加载图表依赖。
4. 长结果、历史列表和日志使用虚拟化或分页。
5. 为异步内容预留尺寸，避免生成结果造成 CLS。

## 9. 分阶段路线图

### Phase A：语义与认证底线，1-2 天

本轮已执行：路由认证、代理可配置、H1、main、label、提交 loading、内联错误。

### Phase B：组件状态与认证页收口，2-4 天

1. 提取 AuthBrandSection、AuthFormShell、InlineAlert、AsyncButton。
2. 统一所有表单 label/helper/error。
3. 修复 Skill 文案口径与结果动作。

### Phase C：内容与后台页面，4-7 天

1. 历史、详情、统计、用户管理完成响应式和状态补齐。
2. 图表增加文本摘要和数据表替代。
3. 清理硬编码颜色和重复卡片样式。

### Phase D：性能与回归，3-5 天

1. Ant Design 与 ECharts 模块化。
2. Lighthouse、键盘、375/768/1024/1440 回归。
3. 建立构建体积基线和 PR 检查。
4. 已新增串行 `npm run check` 门禁，覆盖 ESLint、类型检查、生产构建、体积预算、状态测试和 Playwright UI 回归。
5. Playwright 核心路由检查已覆盖四档视口、表单语义、键盘焦点、reduced-motion 和运行时错误。

## 10. 验收清单

1. 每个页面只有一个 H1，每个应用视图只有一个 main landmark。
2. 仅用键盘可以完成登录、注册、创建、Skill 执行和结果操作。
3. 所有输入都有 label，错误靠近字段或表单，并提供恢复动作。
4. 未登录访问受保护路由会跳转登录页，登录成功后返回原路径。
5. 375、768、1024、1440 无横向滚动和内容遮挡。
6. reduced-motion 下无持续动画和非必要位移。
7. 构建、类型检查、现有测试全部通过。
8. 页面控制台无未处理异常，API 失败有用户可理解反馈。

## 11. 本轮实施记录

已完成：

1. Vite API proxy 支持 `VITE_API_PROXY_TARGET`，默认仍为 8567。
2. `/create`、`/article/list`、`/article/:taskId`、`/vip` 增加 requiresAuth。
3. 全局品牌标题从 H1 改为普通文本，装饰 logo 使用空 alt。
4. ArticleCreate、SkillCenter、SkillExecute 移除嵌套 main。
5. 首页选题、创作选题、登录和注册输入补充 label 关联。
6. 登录与注册增加提交 loading、禁用状态、自动填充和内联错误。
7. 浏览器验证 `/skill/:skillName` 会跳转至 `/user/login?redirect=原路径`，并显示统一登录提示。
8. 浏览器验证无效登录会在表单内保留“用户不存在或密码错误”，且通过 `aria-live="polite"` 对辅助技术播报。
9. 最终首页 DOM 验收为单一 H1、单一 main、无嵌套 main、无横向溢出，选题输入具备 label 与帮助文本关联。

当前限制：Lighthouse 的完整 Web Vitals 采样仍需在独立性能环境中执行；现有构建体积预算、Playwright UI 门禁和四档视口回归已经固化到前端项目。
