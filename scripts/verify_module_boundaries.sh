#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASELINE="scripts/module_boundary_baseline.txt"
if [ ! -f "$BASELINE" ]; then
  echo "Missing module boundary baseline: $BASELINE" >&2
  exit 1
fi

tmp_current="$(mktemp)"
tmp_baseline="$(mktemp)"
tmp_new="$(mktemp)"
tmp_stale="$(mktemp)"
tmp_forbidden_deps="$(mktemp)"
trap 'rm -f "$tmp_current" "$tmp_baseline" "$tmp_new" "$tmp_stale" "$tmp_forbidden_deps"' EXIT

required_deep_xiaomi_scan_roots=(
  "manager/src/main/java"
  "settings/src/main/java"
  "xmsf/src/main/java/io/github/magisk317/mipush/app"
)

required_manager_app_scan_roots=(
  "manager/src/main/java"
  "settings/src/main/java"
)

deep_xiaomi_pattern='^import com\.xiaomi\.(channel|mipush|network|push|smack|slim|stats|tinyData|xmpush)'
manager_app_pattern='^import io\.github\.magisk317\.mipush\.app\.'
deep_xiaomi_string_pattern='"com\.xiaomi\.(channel|mipush|network|push|smack|slim|stats|tinyData|xmpush)'

require_scan_roots() {
  local root
  for root in "$@"; do
    if [ ! -d "$root" ]; then
      echo "Configured module-boundary scan root is missing: $root" >&2
      echo "Update scripts/verify_module_boundaries.sh when module source roots move." >&2
      exit 1
    fi
  done
}

require_scan_roots "${required_deep_xiaomi_scan_roots[@]}"
require_scan_roots "${required_manager_app_scan_roots[@]}"

{
  for root in "${required_deep_xiaomi_scan_roots[@]}"; do
    rg -n "$deep_xiaomi_pattern" "$root" || true
  done

  for root in "${required_manager_app_scan_roots[@]}"; do
    rg -n "$manager_app_pattern" "$root" || true
  done

  rg -n "$deep_xiaomi_string_pattern" "manager/src/main/java" "settings/src/main/java" || true
} | while IFS=: read -r path _line import_line; do
  [ -n "${path:-}" ] || continue
  printf '%s|%s\n' "$path" "$import_line"
done | sort -u > "$tmp_current"

sed -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$BASELINE" | sort -u > "$tmp_baseline"

comm -13 "$tmp_baseline" "$tmp_current" > "$tmp_new"
comm -23 "$tmp_baseline" "$tmp_current" > "$tmp_stale"

if [ -s "$tmp_new" ]; then
  echo "New architecture-boundary imports were added outside the allowed adapter areas." >&2
  echo "Move the dependency behind an xmsf runtime/bridge adapter, manager gateway, or explicit shared contract." >&2
  echo "Only update the baseline for deliberate, documented compatibility debt." >&2
  echo >&2
  cat "$tmp_new" >&2
  exit 1
fi

if [ -s "$tmp_stale" ]; then
  echo "Module boundary baseline contains stale entries." >&2
  echo "Remove entries that no longer appear in the scanned source roots." >&2
  echo >&2
  cat "$tmp_stale" >&2
  exit 1
fi

manager_build_file="manager/build.gradle.kts"
if [ -f "$manager_build_file" ]; then
  rg -n 'project\(":(vendor|xmsf|pinned)"\)' "$manager_build_file" \
    | while IFS=: read -r path _line import_line; do
      [ -n "${path:-}" ] || continue
      printf '%s|%s\n' "$path" "$import_line"
    done > "$tmp_forbidden_deps" || true
fi

if [ -s "$tmp_forbidden_deps" ]; then
  echo "Manager build script contains forbidden project dependencies." >&2
  echo "Manager should only depend on shared contracts, settings, core, common, and ui-kit." >&2
  echo >&2
  cat "$tmp_forbidden_deps" >&2
  exit 1
fi

echo "Module boundary check passed."
