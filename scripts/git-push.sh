#!/bin/bash
# git-push.sh — 带重试的 git 推送脚本 + pre-push 本地质量闸门
# 解决 GitHub 偶发网络/慢速导致的 push 失败（RPC failed / connection reset）
# 以及「本地绿、CI 红」导致的 CI 失败邮件（在推送前先跑与 CI 一致的确定性检查）
# 用法: bash scripts/git-push.sh [remote] [branch]
#       默认: bash scripts/git-push.sh github dev
# 跳过检查: SKIP_PUSH_CHECK=1 bash scripts/git-push.sh github dev  (仅限紧急热修)

REMOTE="${1:-github}"
BRANCH="${2:-$(git branch --show-current)}"
MAX_RETRY=3
DELAY=5

# ── pre-push 质量闸门：与 CI 一致的最小确定性检查，失败即中止推送 ──
if [ -z "$SKIP_PUSH_CHECK" ]; then
    echo "→ pre-push: 本地质量检查（失败将中止推送；紧急时 SKIP_PUSH_CHECK=1 跳过）..."

    CHECK_LOG=$(mktemp)
    if ! (cd frontend && npm run type-check && npm run lint:check && npm run test:skill) >"$CHECK_LOG" 2>&1; then
        echo "❌ 前端检查未过（type-check / lint / 单测），已中止推送。"
        echo "   原因（尾部）："
        tail -20 "$CHECK_LOG"
        echo "   修复后重试；或确认紧急热修时用 SKIP_PUSH_CHECK=1 强制推送。"
        rm -f "$CHECK_LOG"
        exit 1
    fi
    if ! mvn -q -Dspring.profiles.active=test test-compile >"$CHECK_LOG" 2>&1; then
        echo "❌ 后端编译未过，已中止推送。"
        echo "   原因（尾部）："
        tail -20 "$CHECK_LOG"
        echo "   修复后重试；或确认紧急热修时用 SKIP_PUSH_CHECK=1 强制推送。"
        rm -f "$CHECK_LOG"
        exit 1
    fi
    rm -f "$CHECK_LOG"
    echo "✓ 本地质量检查通过"
fi

echo "→ 推送 $REMOTE/$BRANCH (最多重试 $MAX_RETRY 次)..."

for attempt in $(seq 1 "$MAX_RETRY"); do
    echo ""
    echo "--- 尝试 $attempt/$MAX_RETRY ---"
    git push "$REMOTE" "$BRANCH"
    status=$?
    if [ "$status" -eq 0 ]; then
        echo ""
        echo "✅ 推送成功: $REMOTE/$BRANCH"
        exit 0
    fi
    echo "⚠️  推送失败 (exit $status)"

    if [ "$attempt" -lt "$MAX_RETRY" ]; then
        echo "   等待 ${DELAY}s 后重试..."
        sleep "$DELAY"
        DELAY=$((DELAY * 2))  # 指数退避: 5s, 10s, ...
    fi
done

echo ""
echo "❌ 推送失败 $MAX_RETRY 次。建议:"
echo "   1. 检查网络: ping github.com"
echo "   2. 确认 git http 配置: git config --global --list | grep http"
echo "   3. 手动重试: git push $REMOTE $BRANCH"
exit 1
