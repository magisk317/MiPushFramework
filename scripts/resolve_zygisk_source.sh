#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ZYGISK_DIR="${1:-${MIPUSH_ZYGISK_SOURCE_DIR:-${ROOT_DIR}/MiPushZygisk}}"
ZYGISK_REPOSITORY="${MIPUSH_ZYGISK_REPOSITORY:-https://gitlab.com/magisk3171/MiPushZygisk.git}"
ZYGISK_REF="${MIPUSH_ZYGISK_REF:-3085c59d43c4e328369eadbcf6c1c079479b0d8f}"

if [[ -z "$ZYGISK_REF" ]]; then
  echo "ERROR: MIPUSH_ZYGISK_REF must be a branch, tag, or commit SHA" >&2
  exit 1
fi

if [[ -n "${MIPUSH_ZYGISK_SOURCE_DIR:-}" && "$ZYGISK_DIR" == "$MIPUSH_ZYGISK_SOURCE_DIR" ]]; then
  if [[ ! -d "$ZYGISK_DIR" ]]; then
    echo "ERROR: MIPUSH_ZYGISK_SOURCE_DIR does not exist: $ZYGISK_DIR" >&2
    exit 1
  fi
  printf '%s\n' "$ZYGISK_DIR"
  exit 0
fi

if ! git ls-remote --exit-code "$ZYGISK_REPOSITORY" HEAD >/dev/null 2>&1; then
  echo "ERROR: MiPushZygisk source repository is unavailable" >&2
  exit 2
fi

cached_target_dir=""
restore_cached_target() {
  if [[ -n "$cached_target_dir" && -d "$cached_target_dir/module/target" ]]; then
    mkdir -p "$ZYGISK_DIR/module"
    rm -rf -- "$ZYGISK_DIR/module/target"
    mv "$cached_target_dir/module/target" "$ZYGISK_DIR/module/target"
  fi
}
trap restore_cached_target EXIT

if [[ ! -d "$ZYGISK_DIR/.git" ]]; then
  if [[ -d "$ZYGISK_DIR/module/target" ]]; then
    cached_target_dir="$(mktemp -d)"
    mkdir -p "$cached_target_dir/module"
    mv "$ZYGISK_DIR/module/target" "$cached_target_dir/module/target"
  fi
  rm -rf -- "$ZYGISK_DIR"
  mkdir -p "$(dirname "$ZYGISK_DIR")"
  git init --quiet "$ZYGISK_DIR"
  git -C "$ZYGISK_DIR" remote add origin "$ZYGISK_REPOSITORY"
elif git -C "$ZYGISK_DIR" remote get-url origin >/dev/null 2>&1; then
  git -C "$ZYGISK_DIR" remote set-url origin "$ZYGISK_REPOSITORY"
else
  git -C "$ZYGISK_DIR" remote add origin "$ZYGISK_REPOSITORY"
fi

fetch_attempts="${MIPUSH_ZYGISK_FETCH_ATTEMPTS:-3}"
fetch_attempt=1
while ! git -C "$ZYGISK_DIR" fetch --depth 1 origin "$ZYGISK_REF"; do
  if (( fetch_attempt >= fetch_attempts )); then
    echo "ERROR: configured MiPushZygisk ref '$ZYGISK_REF' is not fetchable after ${fetch_attempts} attempts" >&2
    exit 1
  fi
  echo "WARN: MiPushZygisk fetch attempt ${fetch_attempt}/${fetch_attempts} failed; retrying in 5s..." >&2
  sleep 5
  fetch_attempt=$((fetch_attempt + 1))
done
git -C "$ZYGISK_DIR" checkout --detach --force FETCH_HEAD

if [[ "$ZYGISK_REF" =~ ^[0-9a-fA-F]{40}$ ]]; then
  resolved_ref="$(git -C "$ZYGISK_DIR" rev-parse HEAD)"
  normalized_ref="$(printf '%s' "$ZYGISK_REF" | tr '[:upper:]' '[:lower:]')"
  if [[ "$resolved_ref" != "$normalized_ref" ]]; then
    echo "ERROR: resolved MiPushZygisk commit $resolved_ref does not match pinned ref $ZYGISK_REF" >&2
    exit 1
  fi
fi

printf '%s\n' "$ZYGISK_DIR"
