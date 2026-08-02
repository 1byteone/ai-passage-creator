# Java / Spring Boot 后端规范

> **加载条件**: 当修改 `src/main/java/**/*.java` 或 `src/test/java/**/*.java` 时适用。

---

## 分层架构 (严格执行)

```
Controller → Service → Mapper → DB
    ↓           ↓
  DTO/VO      Entity
```

### Controller 层规范

- **职责**: 参数校验 + 调用 Service + 返回响应。**禁止写业务逻辑**。
- **注解**: `@RestController` + `@RequestMapping("/api/xxx")`
- **响应**: 统一使用 `BaseResponse<T>` 包装。成功 `BaseResponse.success(data)`，失败抛异常由 `GlobalExceptionHandler` 处理。
- **入参**: 前台传参用 DTO (`@Valid` + Bean Validation)，路径参数用 `@PathVariable`。
- **分页**: 统一使用 MyBatis-Flex 的 `Page<T>` 对象，前端传入 `pageNum` + `pageSize`。

### Service 层规范

- **职责**: 业务逻辑 + 事务管理 + 编排 Mapper 调用。
- **事务**: 写操作加 `@Transactional(rollbackFor = Exception.class)`。
- **异常**: 业务异常抛出自定义异常 (继承 `RuntimeException`)，不要返回 null 或 error code。
- **注入**: 构造器注入 (`@RequiredArgsConstructor` + `private final`)，不要用 `@Autowired` 字段注入。

### Mapper 层规范

- **ORM**: MyBatis-Flex 1.11.1 (`com.mybatis-flex`)。**不是 MyBatis-Plus!** 不要使用 `com.baomidou.mybatisplus` 的 API。
- **接口**: 继承 `BaseMapper<Entity>` 获得 CRUD 能力。
- **复杂查询**: 写在 `resources/mapper/` 下同名 XML 文件中，或使用 MyBatis-Flex QueryWrapper。

---

## 命名规范

| 元素 | 风格 | 示例 |
|------|------|------|
| 类/接口 | PascalCase | `ArticleServiceImpl`, `ArticleMapper` |
| 方法/变量 | camelCase | `createTask()`, `articleService` |
| 常量 | UPPER_SNAKE | `MAX_RETRY_COUNT` |
| 包名 | lowercase | `com.example.aipassagecreator.service` |
| DTO | `XxxDTO` / `XxxRequest` | `ArticleCreateRequest` |
| VO | `XxxVO` | `ArticleDetailVO` |
| Entity | 与表名对应 | `Article` → 表 `article` |

---

## 数据库 & 模式管理

- **生产**: MySQL 8.0+ (`ai_passage_creator` 库)
- **测试**: H2 `MODE=MySQL` (配置在 `application-test.yml`)
- **迁移 SQL**: 放在 `src/main/resources/sql/` (无 Flyway!)
- **Session**: Redis 存储 (`spring-session-data-redis`)，超期 30 天
- **注意**: CI 测试时无 Redis → 配置 `spring.session.store-type=none`

---

## AI 集成

- **SDK**: Spring AI Alibaba 1.1.0-RC2 + Spring AI OpenAI Starter 1.1.0
- **Agent**: `spring-ai-alibaba-agent-framework` 编排多 Agent 协作
- **模型**: DashScope (Qwen) 为主，Agnes AI / SenseNova 为可替换提供方
- **流式**: SSE (Server-Sent Events) 推流，后端 `SseEmitter` / `Flux<ServerSentEvent>`

---

## 测试规范

- **框架**: JUnit 5 + Mockito + Spring Boot Test
- **命名**: `ClassNameTest` (类级)，`methodName_scenario_expectedResult()` (方法级)
- **分层**: 
  - 单元测试: Mock Service/Mapper 依赖
  - 集成测试: `@SpringBootTest` + `application-test.yml` (H2)
- **数据**: `src/test/resources/` 下放测试专用 SQL

---

## 日志 & 可观测性

- **日志**: SLF4J + Logback，使用 `@Slf4j` (Lombok)
- **监控**: Actuator + Micrometer + Prometheus
- **不要**使用 `System.out.println()` / `e.printStackTrace()`

---

## 性能 & 安全

- 大查询分页，禁止 `SELECT *` 无 LIMIT 的全表扫描
- COS 文件上传后返回预签名 URL (`PresignedUrl`)，不直接暴露存储路径
- Stripe Webhook 验签 (`StripeWebhookController`)，不信任未验证的回调
- 所有用户输入必须后端二次校验 (防前端绕过)
