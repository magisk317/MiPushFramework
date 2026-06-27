#!/usr/bin/env bash
set -euo pipefail

# 检查必要工具
if ! command -v zip >/dev/null 2>&1; then
  echo "ERROR: 'zip' command is required but not installed." >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

XMSF_APK="${1:-}"
MIPUSH_APK="${2:-}"
VERSION_NAME="${3:-}"

# 派生版本号
if [[ -z "$VERSION_NAME" ]]; then
  VERSION_NAME="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)"/\1/p' gradle/libs.versions.toml 2>/dev/null || echo "")"
fi
if [[ -z "$VERSION_NAME" ]]; then
  VERSION_NAME="$(date +%Y%m%d)"
fi

XMSF_PACKAGE="com.xiaomi.xmsf"
MIPUSH_PACKAGE="io.github.magisk317.mipush"
MODULE_NAME="MiPushFramework-Magisk-${VERSION_NAME}"
BUILD_DIR="$ROOT_DIR/build/magisk-module"
OUTPUT_DIR="$ROOT_DIR/build/output"

# 如果 APK 路径未通过参数传递，则自动查找（但不推荐，CI 中已显式传入）
if [[ -z "$XMSF_APK" ]]; then
  XMSF_APK="$(find app/build/outputs/apk/normal -type f -name '*arm64-v8a*.apk' 2>/dev/null | head -n 1 || true)"
fi
if [[ -z "$MIPUSH_APK" ]]; then
  MIPUSH_APK="$(find mipush/build/outputs/apk -type f -name '*arm64-v8a*.apk' 2>/dev/null | head -n 1 || true)"
fi

# 严格检查 APK 是否存在
if [[ -z "$XMSF_APK" ]] || [[ ! -f "$XMSF_APK" ]]; then
  echo "ERROR: XMSF APK not found at '$XMSF_APK'" >&2
  exit 1
fi
if [[ -z "$MIPUSH_APK" ]] || [[ ! -f "$MIPUSH_APK" ]]; then
  echo "ERROR: MiPush APK not found at '$MIPUSH_APK'" >&2
  exit 1
fi

echo "XMSF APK:   $XMSF_APK ($(du -h "$XMSF_APK" | awk '{print $1}'))"
echo "MiPush APK: $MIPUSH_APK ($(du -h "$MIPUSH_APK" | awk '{print $1}'))"
echo "Version:    $VERSION_NAME"

# 准备临时目录
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/system/app/$XMSF_PACKAGE"
mkdir -p "$BUILD_DIR/system/app/$MIPUSH_PACKAGE"

# 复制 APK（增加 -v 详细输出）
echo "Copying XMSF APK..."
cp -v "$XMSF_APK" "$BUILD_DIR/system/app/$XMSF_PACKAGE/base.apk"
echo "Copying MiPush APK..."
cp -v "$MIPUSH_APK" "$BUILD_DIR/system/app/$MIPUSH_PACKAGE/base.apk"

# 校验复制结果
echo "Verifying staged files..."
if [[ ! -f "$BUILD_DIR/system/app/$XMSF_PACKAGE/base.apk" ]]; then
  echo "ERROR: XMSF base.apk not found after copy!" >&2
  exit 1
fi
if [[ ! -f "$BUILD_DIR/system/app/$MIPUSH_PACKAGE/base.apk" ]]; then
  echo "ERROR: MiPush base.apk not found after copy!" >&2
  exit 1
fi

echo "Staged directory tree:"
ls -lR "$BUILD_DIR"

# 创建 module.prop
cat > "$BUILD_DIR/module.prop" << PROP
id=MiPushFramework
name=MiPush Framework
version=${VERSION_NAME}
versionCode=$(date +%Y%m%d)
author=MiPush Contributors
description=MiPush Framework Magisk module - installs XMSF and MiPush as system apps for enhanced push compatibility.
PROP

# 创建 customize.sh
cat > "$BUILD_DIR/customize.sh" << 'CUSTOMIZE'
#!/sbin/sh
ui_print "- Installing MiPush Framework as system apps..."
ui_print "  - com.xiaomi.xmsf (XMSF)"
ui_print "  - io.github.magisk317.mipush (MiPush)"

set_perm_recursive "$MODPATH/system/app" 0 0 0755 0644
CUSTOMIZE
chmod +x "$BUILD_DIR/customize.sh"

# 打包
mkdir -p "$OUTPUT_DIR"
ZIP_FILE="$OUTPUT_DIR/${MODULE_NAME}.zip"
rm -f "$ZIP_FILE"
echo "Creating zip: $ZIP_FILE"
(cd "$BUILD_DIR" && zip -r9 "$ZIP_FILE" . -x '*.git*' >/dev/null)

echo ""
echo "Magisk module created: $ZIP_FILE"
echo "   Size: $(du -h "$ZIP_FILE" | awk '{print $1}')"
echo "Contents of module zip:"
unzip -l "$ZIP_FILE"