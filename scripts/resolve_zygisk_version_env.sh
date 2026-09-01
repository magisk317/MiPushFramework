#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${MIPUSH_FRAMEWORK_ROOT:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
VERSION_FILE="${MIPUSH_FRAMEWORK_VERSION_FILE:-$ROOT_DIR/gradle/libs.versions.toml}"

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

MIPUSH_ZYGISK_VERSION_NAME="${MIPUSH_ZYGISK_VERSION_NAME:-$(extract_toml_value "versionName" "$VERSION_FILE")}"
MIPUSH_ZYGISK_VERSION_CODE="${MIPUSH_ZYGISK_VERSION_CODE:-$(extract_toml_value "versionCode" "$VERSION_FILE")}"

if [[ -z "$MIPUSH_ZYGISK_VERSION_NAME" || -z "$MIPUSH_ZYGISK_VERSION_CODE" ]]; then
  echo "ERROR: failed to parse MiPush version for Zygisk build from $VERSION_FILE" >&2
  if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    exit 1
  fi
  return 1
fi

export MIPUSH_ZYGISK_VERSION_NAME
export MIPUSH_ZYGISK_VERSION_CODE

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  printf 'MIPUSH_ZYGISK_VERSION_NAME=%q\n' "$MIPUSH_ZYGISK_VERSION_NAME"
  printf 'MIPUSH_ZYGISK_VERSION_CODE=%q\n' "$MIPUSH_ZYGISK_VERSION_CODE"
fi
