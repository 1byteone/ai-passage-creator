# ─── Stage 1: Build ───
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package -q

# ─── Stage 2: Runtime ───
FROM eclipse-temurin:21-jre-alpine

# 系统依赖: Mermaid CLI + Playwright chromium + CJK 字体 + tzdata
RUN apk add --no-cache \
    npm \
    fontconfig \
    tzdata \
    # Playwright chromium runtime deps
    nss freetype harfbuzz ca-certificates ttf-freefont \
    udev dbus-libs libx11 libxcomposite libxdamage libxext libxfixes \
    libxrandr mesa-gbm alsa-lib at-spi2-core cups-libs libdrm \
    libxkbcommon pango cairo gtk+3.0 \
    # CJK 字体 (Playwright 渲染中文卡片)
    font-noto-cjk \
    && npm install -g @mermaid-js/mermaid-cli \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 安装中文字体（PDF/Word 导出用）
RUN apk add --no-cache wqy-zenhei --repository=http://dl-cdn.alpinelinux.org/alpine/edge/testing

# Playwright: 安装 chromium 浏览器（构建期安装，生产首次启动即用）
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN npx playwright install chromium --with-deps 2>/dev/null; \
    npx playwright install-deps chromium 2>/dev/null; \
    echo "Playwright chromium installed"; \
    ls -la /ms-playwright/ || true

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# 非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN chown -R appuser:appgroup /app /ms-playwright
USER appuser

EXPOSE 8567

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8567/api/health/ || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
