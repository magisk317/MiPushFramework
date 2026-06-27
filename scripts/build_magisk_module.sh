#!/usr/bin/env bash
# Build a Magisk module zip containing XMSF and MiPush APKs
# as system apps placed under system/app/<package-name>/base.apk
#
# Usage:
#   ./scripts/build_magisk_module.sh          # auto-detect from build outputs
#   ./scripts/build_magisk_module.sh <xmsf_apk> <mipush_apk> [version]

set -euo pipefail

# Check required tools
if ! command -v zip >/dev/null 2>&1; then
  echo "ERROR: 'zip' command is required but not installed." >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Config (overridable via args)
XMSF_APK="${1:-}"
MIPUSH_APK="${2:-}"
VERSION_NAME="${3:-}"

# Derive version from gradle properties if not provided
if [[ -z "$VERSION_NAME" ]]; then
  # Allow leading whitespace in libs.versions.toml
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

# Resolve APK files (only arm64-v8a, file type guaranteed) only if not provided
if [[ -z "$XMSF_APK" ]]; then
  XMSF_APK="$(find app/build/outputs/apk/normal -type f -name '*arm64-v8a*.apk' 2>/dev/null | head -n 1 || true)"
fi
if [[ -z "$MIPUSH_APK" ]]; then
  MIPUSH_APK="$(find mipush/build/outputs/apk -type f -name '*arm64-v8a*.apk' 2>/dev/null | head -n 1 || true)"
fi

if [[ -z "$XMSF_APK" ]] || [[ ! -f "$XMSF_APK" ]]; then
  echo "ERROR: XMSF arm64-v8a APK not found." >&2
  exit 1
fi
if [[ -z "$MIPUSH_APK" ]] || [[ ! -f "$MIPUSH_APK" ]]; then
  echo "ERROR: MiPush arm64-v8a APK not found." >&2
  exit 1
fi

echo "XMSF APK:   $XMSF_APK"
echo "MiPush APK: $MIPUSH_APK"
echo "Version:    $VERSION_NAME"

# 打印文件大小以确认
echo "XMSF APK size: $(du -h "$XMSF_APK" | awk '{print $1}')"
echo "MiPush APK size: $(du -h "$MIPUSH_APK" | awk '{print $1}')"

# Prepare staging directory
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/system/app/$XMSF_PACKAGE"
mkdir -p "$BUILD_DIR/system/app/$MIPUSH_PACKAGE"

# Copy APKs as base.apk
cp -v "$XMSF_APK"   "$BUILD_DIR/system/app/$XMSF_PACKAGE/base.apk"
cp -v "$MIPUSH_APK" "$BUILD_DIR/system/app/$MIPUSH_PACKAGE/base.apk"

# module.prop
cat > "$BUILD_DIR/module.prop" << PROP
id=MiPushFramework
name=MiPush Framework
version=${VERSION_NAME}
versionCode=$(date +%Y%m%d)
author=MiPush Contributors
description=MiPush Framework Magisk module - installs XMSF and MiPush as system apps for enhanced push compatibility.
PROP

# customize.sh
cat > "$BUILD_DIR/customize.sh" << 'CUSTOMIZE'
#!/sbin/sh
ui_print "- Installing MiPush Framework as system apps..."
ui_print "  - com.xiaomi.xmsf (XMSF)"
ui_print "  - io.github.magisk317.mipush (MiPush)"

set_perm_recursive "$MODPATH/system/app" 0 0 0755 0644
CUSTOMIZE

chmod +x "$BUILD_DIR/customize.sh"

# Create zip
mkdir -p "$OUTPUT_DIR"
ZIP_FILE="$OUTPUT_DIR/${MODULE_NAME}.zip"

rm -f "$ZIP_FILE"
(cd "$BUILD_DIR" && zip -r9 "$ZIP_FILE" . -x '*.git*' >/dev/null)

echo ""
echo "Magisk module created: $ZIP_FILE"
echo "   Size: $(du -h "$ZIP_FILE" | awk '{print $1}')"
echo "Contents of module zip:"
unzip -l "$ZIP_FILE" | head -20