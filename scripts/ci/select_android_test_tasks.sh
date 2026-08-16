#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
toolkit_dir="${1:-${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}}"
paths_file="${2:-}"
full_shards=(
  common-notification common-utils common-other core
  xposed-systemui xposed-island xposed-other
  xmsf-stock xmsf-notification xmsf-service xmsf-manager xmsf-runtime xmsf-other
  manager-events manager-main manager-connection manager-other
)

if [[ -n "${CI_COMMIT_TAG:-}" || "${GITHUB_REF_TYPE:-}" == tag ||
  "${CI_COMMIT_BRANCH:-}" == beta || "${CI_COMMIT_BRANCH:-}" == master ||
  "${GITHUB_REF_NAME:-}" == beta || "${GITHUB_REF_NAME:-}" == master ]]; then
  printf '%s\n' "${full_shards[@]}"
  exit 0
fi

if [[ -z "$paths_file" ]]; then
  paths_file="$(mktemp)"
  trap 'rm -f "$paths_file"' EXIT
  bash "$toolkit_dir/ci/changed_paths.sh" "$paths_file"
fi
if [[ "$(sed -n '1p' "$paths_file")" == full ]]; then
  printf '%s\n' "${full_shards[@]}"
  exit 0
fi

declare -A selected=()
select_task() { selected["$1"]=1; }
has_source_change=0
while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  case "$path" in
    impact|.gitlab-ci.yml|.github/workflows/*|docs/*|README*|LICENSE*|CHANGELOG*) continue ;;
    build.gradle*|settings.gradle*|gradle.properties|gradle/*|build-logic/*|.gitmodules|scripts/*|.magisk-ci-toolkit/*)
      printf '%s\n' "${full_shards[@]}"; exit 0 ;;
    common/*)
      select_task common-notification; select_task common-utils; select_task common-other ;;
    core/*)
      select_task core; select_task xmsf-runtime; select_task xmsf-service ;;
    xmsf/*) select_task xmsf-stock; select_task xmsf-notification; select_task xmsf-service
      select_task xmsf-runtime; select_task xmsf-other ;;
    xposed/*) select_task xposed-systemui; select_task xposed-island; select_task xposed-other ;;
    manager/*|manager-api/*|manager-client/*|settings/*|configuration/*|vendor/*|pinned/*)
      select_task manager-events; select_task manager-main; select_task manager-connection; select_task manager-other ;;
    app/*) select_task app-compile ;;
    mipush/*) select_task mipush-compile ;;
    *) has_source_change=1 ;;
  esac
done < <(sed -n '2,$p' "$paths_file")

if (( has_source_change )); then
  printf '%s\n' "${full_shards[@]}"
  exit 0
fi
if ((${#selected[@]} > 0)); then printf '%s\n' "${!selected[@]}" | sort; fi
