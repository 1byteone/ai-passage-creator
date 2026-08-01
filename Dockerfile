# ─── Stage 1: Build ───
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package -q

# ─── Stage 2: Runtime ───
FROM eclipse-temurin:21-jre-alpine

# 系统依赖: Mermaid CLI + 中文字体 + tzdata
RUN apk add --no-cache \
    npm \
    fontconfig \
    tzdata \
    && npm install -g @mermaid-js/mermaid-cli \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 安装中文字体（防止 PDF/图片导出乱码）
RUN apk add --no-cache wqy-zenhei --repository=http://dl-cdn.alpinelinux.org/alpine/edge/testing

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# 非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8567

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8567/api/health/ || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
