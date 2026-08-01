#!/bin/bash
# git-push.sh — 带重试的 git 推送脚本
# 解决 GitHub 偶发网络/慢速导致的 push 失败（RPC failed / connection reset）
# 用法: bash scripts/git-push.sh [remote] [branch]
#       默认: bash scripts/git-push.sh github dev

REMOTE="${1:-github}"
BRANCH="${2:-$(git branch --show-current)}"
MAX_RETRY=3
DELAY=5

echo "→ 推送 $REMOTE/$BRANCH (最多重试 $MAX_RETRY 次)..."

for attempt in $(seq 1 "$MAX_RETRY"); do
    echo ""
    echo "--- 尝试 $attempt/$MAX_RETRY ---"
    if git push "$REMOTE" "$BRANCH" 2>&1; then
        echo ""
        echo "✅ 推送成功: $REMOTE/$BRANCH"
        exit 0
    fi
    status=$?
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
