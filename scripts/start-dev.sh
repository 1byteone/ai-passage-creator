#!/bin/bash
# start-dev.sh — 一键启动开发环境
# 用法: bash scripts/start-dev.sh
#
# 固化凭据来源：
#   MySQL: mysql-integration skill
#   Redis: redis.windows.conf requirepass
#   Agnes: agnes-ai-integration skill
#   DashScope: 系统环境变量
#
# 凭据说明（绝对不写入代码，仅在此脚本中引用）：
#   AGNES_AI_API_KEY 来自 agnes-ai-integration skill 文档

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$SCRIPT_DIR"

# ── 配置 ──
MYSQL_PASSWORD='yang801578546yjs'
REDIS_PASSWORD='123456'
AGNES_API_KEY='sk-H2xBJlVMLMLiM9tplNS4zeBchkmVa87ZyAZjZWJVfkLLYWHq'

# ── 前置检查 ──

echo "→ 检查 MySQL 3306..."
if ! netstat -an 2>/dev/null | grep -qE ":3306\s.*LISTENING"; then
  echo "  ⚠️  MySQL 未运行，请启动 MySQL 服务后再试"
  exit 1
fi
echo "  ✅ MySQL 运行中"

echo "→ 检查 Redis 6379..."
if ! netstat -an 2>/dev/null | grep -qE ":6379\s.*LISTENING"; then
  echo "  ⚠️  Redis 未运行，请启动 Redis 服务后再试"
  exit 1
fi
echo "  ✅ Redis 运行中"

# DashScope 来自系统环境变量
if [ -z "$DASHSCOPE_API_KEY" ]; then
  DASHSCOPE_KEY=$(powershell.exe -NoProfile -Command "[Environment]::GetEnvironmentVariable('DASHSCOPE_API_KEY','Machine')" 2>/dev/null | tr -d '\r')
  if [ -n "$DASHSCOPE_KEY" ]; then
    export DASHSCOPE_API_KEY="$DASHSCOPE_KEY"
    echo "  ✅ DashScope key 已从系统环境加载"
  else
    echo "  ⚠️  DASHSCOPE_API_KEY 未配置，AI 功能受限"
  fi
fi

# ── 启动后端 ──

echo ""
echo "================================================"
echo "启动后端 (port 8567, context-path /api)"
echo "================================================"

# 构造参数
ARGS=(
  "--spring.datasource.password=$MYSQL_PASSWORD"
  "--spring.data.redis.password=$REDIS_PASSWORD"
  "--spring.ai.openai.api-key=$AGNES_API_KEY"
  "--app.test-admin-account.enabled=true"
  "--app.test-admin-account.account=admin_test"
  "--app.test-admin-account.password=AdminTest@2026"
  "--app.test-admin-account.display-name=管理员测试账号"
)

# 启动后端
DB_PASSWORD="$MYSQL_PASSWORD" \
AGNES_AI_API_KEY="$AGNES_API_KEY" \
mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="${ARGS[*]}" \
  > /tmp/backend.log 2>&1 &

BACKEND_PID=$!
echo "  后端 PID: $BACKEND_PID"

# ── 等待后端就绪 ──

echo ""
echo "→ 等待后端启动..."
MAX_WAIT=60
for i in $(seq 1 $MAX_WAIT); do
  sleep 1
  if curl -s http://localhost:8567/api/health/ > /dev/null 2>&1; then
    echo "  ✅ 后端就绪 ($i 秒)"
    break
  fi
  if [ $i -eq $MAX_WAIT ]; then
    echo "  ❌ 后端启动超时 ${MAX_WAIT}s，查看日志: tail -50 /tmp/backend.log"
    exit 1
  fi
done

# ── 启动前端 ──

echo ""
echo "================================================"
echo "启动前端 (Vite dev, port 5173)"
echo "================================================"

cd "$SCRIPT_DIR/frontend"
npm run dev > /tmp/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "  前端 PID: $FRONTEND_PID"

sleep 4
if curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/ 2>/dev/null | grep -q "200"; then
  echo "  ✅ 前端就绪"
else
  echo "  ⚠️  前端尚未响应，稍后访问 http://localhost:5173/"
fi

# ── 输出 ──

echo ""
echo "================================================"
echo "  ✅ 开发环境就绪"
echo "================================================"
echo ""
echo "  前端:    http://localhost:5173/"
echo "  后端:    http://localhost:8567/api/"
echo "  健康:    http://localhost:8567/api/health/"
echo ""
echo "  管理员:  admin_test / AdminTest@2026"
echo ""
echo "  停止:    taskkill //F //IM java.exe && taskkill //F //IM node.exe"
echo "  后端日志: tail -f /tmp/backend.log"
echo "  前端日志: tail -f /tmp/frontend.log"
echo "================================================"