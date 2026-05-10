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
trap 'rm -f "$tmp_current" "$tmp_baseline" "$tmp_new"' EXIT

pattern='^import com\.xiaomi\.(channel|mipush|network|push|smack|slim|stats|tinyData|xmpush)'
scan_roots=(
  "xmsf/src/main/java/io/github/magisk317/mipush/app"
  "xmsf/src/main/java/io/github/magisk317/mipush/feature"
  "xmsf/src/main/java/io/github/magisk317/mipush/main/viewmodel"
  "xmsf/src/main/java/io/github/magisk317/mipush/viewmodel"
)

for root in "${scan_roots[@]}"; do
  [ -d "$root" ] || continue
  rg -n "$pattern" "$root" || true
done | while IFS=: read -r path _line import_line; do
  [ -n "${path:-}" ] || continue
  printf '%s|%s\n' "$path" "$import_line"
done | sort -u > "$tmp_current"

sed -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$BASELINE" | sort -u > "$tmp_baseline"

comm -13 "$tmp_baseline" "$tmp_current" > "$tmp_new"

if [ -s "$tmp_new" ]; then
  echo "New deep Xiaomi imports were added outside runtime adapters." >&2
  echo "Move the dependency behind an xmsf runtime/bridge adapter, or update the baseline only for deliberate legacy debt." >&2
  echo >&2
  cat "$tmp_new" >&2
  exit 1
fi

echo "Module boundary check passed."
