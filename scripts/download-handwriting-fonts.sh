#!/usr/bin/env bash
# ============================================================
# 手写字体下载脚本 — 从猫啃网下载免费商用中文字体
# 用法: bash scripts/download-handwriting-fonts.sh
# 下载后自动放入 resources/handwriting/fonts/ 目录
#
# 字体文件较大（5-30MB 每个），不纳入 git 版本管理。
# 下载后运行 scripts/upload-handwriting-fonts.sh 上传到 COS。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
FONTS_DIR="$PROJECT_DIR/src/main/resources/handwriting/fonts"

mkdir -p "$FONTS_DIR"
TMP_DIR=$(mktemp -d)
trap "rm -rf \"$TMP_DIR\"" EXIT

echo "=== 手写字体下载脚本 ==="
echo "下载目录: $FONTS_DIR"
echo ""

# 字体定义: 名称 | 下载URL | 内部文件名前缀
FONTS=(
  "猫啃硬笔楷书|https://oss.maoken.com/猫啃字体/中国大陆/猫啃硬笔楷书0.20_猫啃网.zip|maoken-yingbi"
  "写意体|https://oss.maoken.com/猫啃字体/中国大陆/写意体0.100_猫啃网.zip|xieyi"
  "随峰体Plus|https://oss.maoken.com/猫啃字体/中国港台/随峰体Plus1.002_猫啃网.zip|suifeng-plus"
  "Y式笔书体|https://oss.maoken.com/猫啃字体/中国大陆/Y式笔书体1.401_猫啃网.zip|yshi-bishu"
  "荷塘月色手写体|https://oss.maoken.com/猫啃字体/中国大陆/荷塘月色手写体_猫啃网.zip|hetang-yuese"
  "油茶馓子体|https://oss.maoken.com/猫啃字体/中国大陆/油茶馓子体_猫啃网.zip|youcha-sanzi"
  "白路棒棒手写体|https://oss.maoken.com/猫啃字体/中国大陆/白路棒棒手写体_猫啃网.zip|bailu-bangbang"
)

for font_entry in "${FONTS[@]}"; do
  IFS='|' read -r name url prefix <<< "$font_entry"
  echo "📥 下载: $name"
  echo "    URL: $url"

  ZIP_FILE="$TMP_DIR/$prefix.zip"

  # 下载 ZIP
  if curl -sL -o "$ZIP_FILE" --connect-timeout 10 --max-time 60 "$url" 2>/dev/null; then
    echo "    ✅ 下载完成 ($(du -h "$ZIP_FILE" | cut -f1))"

    # 解压到临时目录
    EXTRACT_DIR="$TMP_DIR/$prefix"
    mkdir -p "$EXTRACT_DIR"

    if unzip -o "$ZIP_FILE" -d "$EXTRACT_DIR" > /dev/null 2>&1; then
      # 找到 TTF 文件
      TTF_COUNT=0
      while IFS= read -r -d '' ttf; do
        BASENAME=$(basename "$ttf")
        # 复制到字体目录，重命名为统一前缀
        cp "$ttf" "$FONTS_DIR/$prefix.ttf" 2>/dev/null || cp "$ttf" "$FONTS_DIR/$BASENAME"
        TTF_COUNT=$((TTF_COUNT + 1))
        echo "    📄 字体文件: $BASENAME"
      done < <(find "$EXTRACT_DIR" -name "*.ttf" -print0 2>/dev/null)

      if [ "$TTF_COUNT" -eq 0 ]; then
        echo "    ⚠️ 未找到 TTF 文件 (可能包含 OTF)"
        find "$EXTRACT_DIR" -name "*.otf" -print0 2>/dev/null | while IFS= read -r -d '' otf; do
          cp "$otf" "$FONTS_DIR/$prefix.otf"
          echo "    📄 OTF 文件: $(basename "$otf")"
        done
      fi
    else
      echo "    ❌ 解压失败"
    fi
  else
    echo "    ❌ 下载失败"
  fi
  echo ""
done

echo "=== 下载完成 ==="
echo "字体目录: $FONTS_DIR"
echo ""
echo "已下载字体:"
ls -lh "$FONTS_DIR"/*.{ttf,otf} 2>/dev/null | awk '{print "  " $NF " (" $5 ")"}'
echo ""
echo "请运行 scripts/upload-handwriting-fonts.sh 上传到 COS"