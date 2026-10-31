#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${1:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
ZYGISK_REPOSITORY="${MIPUSH_ZYGISK_REPOSITORY:-https://gitlab.com/magisk3171/MiPushZygisk.git}"
ZYGISK_REF="${MIPUSH_ZYGISK_REF:-}"
TEMP_DIR=""
RESOLVED_ZYGISK_DIR=""

cleanup() {
  if [[ -n "$TEMP_DIR" ]]; then
    # rm -rf can hit "Directory not empty" on the runner overlayfs even when every
    # entry is already gone; cleanup must never fail the release, so retry briefly
    # and drop the residue (the ephemeral CI workspace discards /tmp anyway).
    local attempt
    for attempt in 1 2 3; do
      rm -rf "$TEMP_DIR" 2>/dev/null && return 0
      sleep 1
    done
    return 0
  fi
}
trap cleanup EXIT

# shellcheck source=resolve_zygisk_version_env.sh
# shellcheck disable=SC1091
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
    # A local source directory must be the commit the pin names, otherwise the gate would
    # validate whatever happens to be checked out while CI builds a different revision.
    if [[ -n "$ZYGISK_REF" ]]; then
      local head_sha
      head_sha="$(git -C "$MIPUSH_ZYGISK_SOURCE_DIR" rev-parse HEAD 2>/dev/null || true)"
      if [[ -z "$head_sha" ]]; then
        echo "ERROR: $MIPUSH_ZYGISK_SOURCE_DIR is not a git checkout; cannot verify it matches $ZYGISK_REF" >&2
        return 1
      fi
      if [[ "$head_sha" != "$ZYGISK_REF" ]]; then
        echo "FAIL: source dir HEAD $head_sha does not match the pinned Zygisk ref $ZYGISK_REF" >&2
        return 1
      fi
    fi
    RESOLVED_ZYGISK_DIR="$MIPUSH_ZYGISK_SOURCE_DIR"
    return 0
  fi

  TEMP_DIR="$(mktemp -d)"
  if resolved_dir="$(
    MIPUSH_ZYGISK_REPOSITORY="$ZYGISK_REPOSITORY" \
      MIPUSH_ZYGISK_REF="$ZYGISK_REF" \
      "$SCRIPT_DIR/resolve_zygisk_source.sh" "$TEMP_DIR/MiPushZygisk"
  )"; then
    resolve_status=0
  else
    resolve_status="$?"
  fi
  case "$resolve_status" in
    0) RESOLVED_ZYGISK_DIR="$resolved_dir" ;;
    2)
      echo "SKIP: MiPushZygisk source is unavailable; version sync cannot be checked."
      return 2
      ;;
    *)
      echo "ERROR: MiPushZygisk source could not be checked out." >&2
      return "$resolve_status"
      ;;
  esac
}

expected_version_name="$MIPUSH_ZYGISK_VERSION_NAME"
expected_version_code="$MIPUSH_ZYGISK_VERSION_CODE"

if resolve_zygisk_dir; then
  resolve_status=0
else
  resolve_status="$?"
fi
case "$resolve_status" in
  0) ;;
  2) exit 0 ;;
  *) exit "$resolve_status" ;;
esac
zygisk_dir="$RESOLVED_ZYGISK_DIR"
build_script="$zygisk_dir/build.sh"
cargo_toml="$zygisk_dir/module/Cargo.toml"
module_prop="$zygisk_dir/magisk/module.prop"

if [[ ! -f "$build_script" || ! -f "$cargo_toml" || ! -f "$module_prop" ]]; then
  echo "ERROR: MiPushZygisk source is missing expected version files under $zygisk_dir" >&2
  exit 1
fi

fail=0
supports_version_injection=false

if grep -Fq "MIPUSH_ZYGISK_VERSION_NAME" "$build_script" && grep -Fq "MIPUSH_ZYGISK_VERSION_CODE" "$build_script"; then
  supports_version_injection=true
  echo "PASS: Zygisk build script accepts MiPush version injection"
else
  echo "FAIL: Zygisk build script does not accept MIPUSH_ZYGISK_VERSION_NAME/MIPUSH_ZYGISK_VERSION_CODE" >&2
  fail=1
fi

cargo_version="$(extract_toml_value "version" "$cargo_toml")"
module_version="$(read_prop_value "version" "$module_prop")"
module_code="$(read_prop_value "versionCode" "$module_prop")"
expected_module_version="v$expected_version_name"

# Release builds inject the framework version, so the source defaults are easy to treat as
# cosmetic. They are not: local builds, module.prop readers and this check all see them, and
# skipping the comparison here is how the defaults once stayed on v1.0.8/19 while the framework
# had moved to 1.1.2/21. Compare them unconditionally; injection only overrides the artifact.
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

if [[ "$supports_version_injection" == true ]]; then
  echo "INFO: release builds inject $expected_version_name/$expected_version_code over the source defaults"
fi

if (( fail != 0 )); then
  echo "MiPush Zygisk version sync check failed." >&2
  exit 1
fi

echo "MiPush Zygisk version sync check passed."
