#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
hook="$repo_root/scripts/telegram_ci_hook.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

mkdir -p "$tmp_dir/ci-dist/io.github.magisk317.mipush" "$tmp_dir/ci-dist/com.xiaomi.xmsf"
printf '%s' '1.2.3-20261122_120000' > "$tmp_dir/ci-dist/io.github.magisk317.mipush/version.txt"
printf '%s' '1.2.3-20261122_120000' > "$tmp_dir/ci-dist/com.xiaomi.xmsf/version.txt"

(
  cd "$tmp_dir"
  CI_PROJECT_DIR="$tmp_dir" bash "$hook"
)
[ ! -e "$tmp_dir/ci-dist/telegram-announcement.txt" ] || {
  echo 'OTA marker was created without a successful Telegram state' >&2
  exit 1
}

printf 'ids=123\nchat=private-chat\n' > "$tmp_dir/.telegram_ci_state"
(
  cd "$tmp_dir"
  CI_PROJECT_DIR="$tmp_dir" CI_PIPELINE_ID=456 CI_COMMIT_SHA=abcdef123456 bash "$hook"
)
marker="$tmp_dir/ci-dist/telegram-announcement.txt"
grep -Fxq 'commit=abcdef123456' "$marker"
grep -Fxq 'manager_version=1.2.3-20261122_120000' "$marker"
grep -Fxq 'runtime_version=1.2.3-20261122_120000' "$marker"
if grep -Eq 'ids=|chat=' "$marker"; then
  echo 'OTA marker leaked Telegram delivery coordinates' >&2
  exit 1
fi

printf '%s' '1.2.3-20261122_120001' > "$tmp_dir/ci-dist/com.xiaomi.xmsf/version.txt"
rm -f "$marker"
if (
  cd "$tmp_dir"
  CI_PROJECT_DIR="$tmp_dir" CI_PIPELINE_ID=457 CI_COMMIT_SHA=abcdef123456 bash "$hook"
); then
  :
fi
[ ! -e "$marker" ] || {
  echo 'OTA marker was created for mismatched manager/runtime versions' >&2
  exit 1
}

echo 'Telegram CI OTA marker tests passed'
