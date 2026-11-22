#!/usr/bin/env bash
set -euo pipefail

# The shared Telegram template creates .telegram_ci_state only after the media
# upload succeeds. Add immutable identity fields so OTA probes can reject a
# marker borrowed from an older successful pipeline on the same branch.
state="${CI_PROJECT_DIR:-.}/.telegram_ci_state"
[ -f "$state" ] || exit 0

project_dir="${CI_PROJECT_DIR:-.}"
manager_version=""
runtime_version=""
[ -f "$project_dir/ci-dist/io.github.magisk317.mipush/version.txt" ] &&
  manager_version="$(tr -d '\r\n' < "$project_dir/ci-dist/io.github.magisk317.mipush/version.txt")"
[ -f "$project_dir/ci-dist/com.xiaomi.xmsf/version.txt" ] &&
  runtime_version="$(tr -d '\r\n' < "$project_dir/ci-dist/com.xiaomi.xmsf/version.txt")"
[ -n "$manager_version" ] && [ "$manager_version" = "$runtime_version" ] || {
  echo "Telegram delivery has no matching manager/runtime CI versions; OTA marker skipped."
  exit 0
}

marker="$project_dir/ci-dist/telegram-announcement.txt"
tmp="${marker}.tmp"
printf 'pipeline_id=%s\ncommit=%s\nmanager_version=%s\nruntime_version=%s\n' \
  "${CI_PIPELINE_ID:-}" "${CI_COMMIT_SHA:-}" "$manager_version" "$runtime_version" > "$tmp"
mv "$tmp" "$marker"
