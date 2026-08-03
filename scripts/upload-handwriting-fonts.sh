#!/usr/bin/env bash
# ============================================================
# 手写字体上传脚本 — 将 resources 中的字体文件上传到 COS
# 用法: bash scripts/upload-handwriting-fonts.sh
# 前置条件: 已配置 COS 环境变量 (TENCENT_COS_*)
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
  echo "请在 $FONTS_DIR 中放入 TTF/OTF 字体文件后重新运行。"
  echo ""
  echo "建议字体（SIL OFL 授权）："
  echo "  1. 手书体 (Shoushu) — 下载: https://github.com/"
  echo "  2. 851手写杂字体 (851tegakizatsu) — 下载: https://github.com/"
  echo "  3. 今年也要加油鸭 (JinnianYeYaoJiaYouYa) — 下载: https://github.com/"
  exit 0
fi

FONT_COUNT=$(find "$FONTS_DIR" -maxdepth 1 -type f \( -name "*.ttf" -o -name "*.otf" \) | wc -l)
echo "找到 $FONT_COUNT 个字体文件"

if [ "$FONT_COUNT" -eq 0 ]; then
  echo "⚠️  未找到字体文件。请将 TTF/OTF 文件放入 $FONTS_DIR"
  exit 0
fi

# 检查 COS 配置
if [ -z "${TENCENT_COS_SECRET_ID:-}" ] || [ -z "${TENCENT_COS_SECRET_KEY:-}" ]; then
  echo "⚠️  COS 环境变量未配置，跳过上传。"
  echo "   需要设置: TENCENT_COS_SECRET_ID, TENCENT_COS_SECRET_KEY, TENCENT_COS_REGION, TENCENT_COS_BUCKET"
  echo ""
  echo "本地文件列表（可手动上传到 COS $COS_PREFIX/）："
  find "$FONTS_DIR" -maxdepth 1 -type f \( -name "*.ttf" -o -name "*.otf" \) -exec basename {} \;
  exit 0
fi

echo "COS Bucket: $TENCENT_COS_BUCKET"
echo "COS Region: ${TENCENT_COS_REGION:-ap-guangzhou}"
echo ""

# 使用 Maven 运行 COS 上传工具类
cd "$PROJECT_DIR"
mvn -q exec:java -Dexec.mainClass="com.example.aipassagecreator.handwriting.tools.FontUploader" \
  -Dspring.profiles.active=prod 2>/dev/null || {
  echo "⚠️  Maven exec 执行失败，尝试直接上传..."

  for font_file in "$FONTS_DIR"/*.ttf "$FONTS_DIR"/*.otf; do
    [ -f "$font_file" ] || continue
    filename=$(basename "$font_file")
    cos_key="$COS_PREFIX/$filename"
    echo "上传: $filename → cos://$cos_key"
    # 使用 COS 工具上传（需安装 coscmd 或使用 SDK）
    if command -v coscmd &>/dev/null; then
      coscmd upload "$font_file" "$cos_key"
    else
      echo "  跳过上传（未安装 coscmd）。文件路径: $font_file"
    fi
  done
}

echo ""
echo "=== 上传完成 ==="
echo "字体 COS 路径: cos://$TENCENT_COS_BUCKET/$COS_PREFIX/"
echo "请更新 application.yml 中的 handwriting.fonts 配置项"