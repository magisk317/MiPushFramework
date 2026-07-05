#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${1:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
ZYGISK_REPOSITORY="${MIPUSH_ZYGISK_REPOSITORY:-https://gitlab.com/magisk3171/MiPushZygisk.git}"
ZYGISK_REF="${MIPUSH_ZYGISK_REF:-main}"
TEMP_DIR=""

cleanup() {
  if [[ -n "$TEMP_DIR" ]]; then
    rm -rf "$TEMP_DIR"
  fi
}
trap cleanup EXIT

MIPUSH_FRAMEWORK_ROOT="$ROOT_DIR" source "$SCRIPT_DIR/resolve_zygisk_version_env.sh"

read_prop_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}=(.*)/\1/p" "$file" | head -n1
}

resolve_zygisk_dir() {
  if [[ -n "${MIPUSH_ZYGISK_SOURCE_DIR:-}" ]]; then
    if [[ ! -d "$MIPUSH_ZYGISK_SOURCE_DIR" ]]; then
      echo "ERROR: MIPUSH_ZYGISK_SOURCE_DIR does not exist: $MIPUSH_ZYGISK_SOURCE_DIR" >&2
      return 1
    fi
    printf '%s\n' "$MIPUSH_ZYGISK_SOURCE_DIR"
    return 0
  fi

  TEMP_DIR="$(mktemp -d)"
  git clone --depth 1 --branch "$ZYGISK_REF" "$ZYGISK_REPOSITORY" "$TEMP_DIR/MiPushZygisk" >/dev/null 2>&1
  printf '%s\n' "$TEMP_DIR/MiPushZygisk"
}

expected_version_name="$MIPUSH_ZYGISK_VERSION_NAME"
expected_version_code="$MIPUSH_ZYGISK_VERSION_CODE"

zygisk_dir="$(resolve_zygisk_dir)"
build_script="$zygisk_dir/build.sh"
cargo_toml="$zygisk_dir/module/Cargo.toml"
module_prop="$zygisk_dir/magisk/module.prop"

if [[ ! -f "$build_script" || ! -f "$cargo_toml" || ! -f "$module_prop" ]]; then
  echo "ERROR: MiPushZygisk source is missing expected version files under $zygisk_dir" >&2
  exit 1
fi

fail=0

if grep -Fq "MIPUSH_ZYGISK_VERSION_NAME" "$build_script" && grep -Fq "MIPUSH_ZYGISK_VERSION_CODE" "$build_script"; then
  echo "PASS: Zygisk build script accepts MiPush version injection"
else
  echo "FAIL: Zygisk build script does not accept MIPUSH_ZYGISK_VERSION_NAME/MIPUSH_ZYGISK_VERSION_CODE" >&2
  fail=1
fi

cargo_version="$(extract_toml_value "version" "$cargo_toml")"
module_version="$(read_prop_value "version" "$module_prop")"
module_code="$(read_prop_value "versionCode" "$module_prop")"
expected_module_version="v$expected_version_name"

if [[ "$cargo_version" == "$expected_version_name" ]]; then
  echo "PASS: Zygisk Cargo version matches $expected_version_name"
else
  echo "FAIL: Zygisk Cargo version mismatch. got '$cargo_version', expected '$expected_version_name'" >&2
  fail=1
fi

if [[ "$module_version" == "$expected_module_version" ]]; then
  echo "PASS: Zygisk module.prop version matches $expected_module_version"
else
  echo "FAIL: Zygisk module.prop version mismatch. got '$module_version', expected '$expected_module_version'" >&2
  fail=1
fi

if [[ "$module_code" == "$expected_version_code" ]]; then
  echo "PASS: Zygisk module.prop versionCode matches $expected_version_code"
else
  echo "FAIL: Zygisk module.prop versionCode mismatch. got '$module_code', expected '$expected_version_code'" >&2
  fail=1
fi

if (( fail != 0 )); then
  echo "MiPush Zygisk version sync check failed." >&2
  exit 1
fi

echo "MiPush Zygisk version sync check passed."
