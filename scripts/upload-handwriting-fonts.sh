#!/usr/bin/env bash
# ============================================================
# 手写字体上传脚本 — 将 resources 中的字体文件上传到 COS
# 用法: bash scripts/upload-handwriting-fonts.sh
# 前置条件: 已配置 COS 环境变量 (TENCENT_COS_SECRET_ID, TENCENT_COS_SECRET_KEY,
#           TENCENT_COS_BUCKET, 可选 TENCENT_COS_REGION)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
FONTS_DIR="$PROJECT_DIR/src/main/resources/handwriting/fonts"
COS_PREFIX="handwriting/fonts"

echo "=== 手写字体上传脚本 ==="
echo "字体目录: $FONTS_DIR"

if [ ! -d "$FONTS_DIR" ]; then
  echo "⚠️  字体目录不存在，创建中..."
  mkdir -p "$FONTS_DIR"
  echo "请先运行 scripts/download-handwriting-fonts.sh 下载字体。"
  exit 0
fi

FONT_COUNT=$(find "$FONTS_DIR" -maxdepth 1 -type f \( -name "*.ttf" -o -name "*.otf" \) | wc -l)
echo "找到 $FONT_COUNT 个字体文件"

if [ "$FONT_COUNT" -eq 0 ]; then
  echo "⚠️  未找到字体文件。请先运行 scripts/download-handwriting-fonts.sh 下载字体。"
  exit 0
fi

# 检查 COS 配置
if [ -z "${TENCENT_COS_SECRET_ID:-}" ] || [ -z "${TENCENT_COS_SECRET_KEY:-}" ] \
   || [ -z "${TENCENT_COS_BUCKET:-}" ]; then
  echo "⚠️  COS 环境变量未配置，跳过上传。"
  echo "   需要设置: TENCENT_COS_SECRET_ID, TENCENT_COS_SECRET_KEY, TENCENT_COS_BUCKET"
  echo "   (可选 TENCENT_COS_REGION，默认 ap-guangzhou)"
  echo ""
  echo "本地字体文件列表（可手动上传到 COS $COS_PREFIX/）："
  find "$FONTS_DIR" -maxdepth 1 -type f \( -name "*.ttf" -o -name "*.otf" \) -exec basename {} \;
  exit 0
fi

echo "COS Bucket: $TENCENT_COS_BUCKET"
echo "COS Region: ${TENCENT_COS_REGION:-ap-guangzhou}"
echo ""

# 通过 FontUploader main 类批量上传
cd "$PROJECT_DIR"
mvn -q compile exec:java \
  -Dexec.mainClass="com.example.aipassagecreator.handwriting.tools.FontUploader"

echo ""
echo "=== 上传完成 ==="
echo "字体 COS 路径: cos://$TENCENT_COS_BUCKET/$COS_PREFIX/"