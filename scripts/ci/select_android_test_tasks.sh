#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
toolkit_dir="${1:-${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}}"
paths_file="${2:-}"
full_tasks=(
  :common:check :xmsf:testNormalDebugUnitTest :xmsf:testVc105DebugUnitTest
  :core:testDebugUnitTest :xposed:testDebugUnitTest qualityGateKoverVerify
)

if [[ -n "${CI_COMMIT_TAG:-}" || "${GITHUB_REF_TYPE:-}" == tag ||
  "${CI_COMMIT_BRANCH:-}" == beta || "${CI_COMMIT_BRANCH:-}" == master ||
  "${GITHUB_REF_NAME:-}" == beta || "${GITHUB_REF_NAME:-}" == master ]]; then
  printf '%s\n' "${full_tasks[@]}"
  exit 0
fi

if [[ -z "$paths_file" ]]; then
  paths_file="$(mktemp)"
  trap 'rm -f "$paths_file"' EXIT
  bash "$toolkit_dir/ci/changed_paths.sh" "$paths_file"
fi
if [[ "$(sed -n '1p' "$paths_file")" == full ]]; then
  printf '%s\n' "${full_tasks[@]}"
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
      printf '%s\n' "${full_tasks[@]}"; exit 0 ;;
    common/*)
      select_task :common:check; select_task :xmsf:testNormalDebugUnitTest
      select_task :xmsf:testVc105DebugUnitTest; select_task :xposed:testDebugUnitTest ;;
    core/*)
      select_task :core:testDebugUnitTest; select_task :xmsf:testNormalDebugUnitTest
      select_task :xmsf:testVc105DebugUnitTest ;;
    xmsf/*) select_task :xmsf:testNormalDebugUnitTest; select_task :xmsf:testVc105DebugUnitTest ;;
    xposed/*) select_task :xposed:testDebugUnitTest ;;
    manager/*|manager-api/*|manager-client/*|settings/*|configuration/*|vendor/*|pinned/*)
      select_task :manager:testDebugUnitTest ;;
    app/*) select_task :app:compileNormalDebugKotlin ;;
    mipush/*) select_task :mipush:compileDebugKotlin ;;
    *) has_source_change=1 ;;
  esac
done < <(sed -n '2,$p' "$paths_file")

if (( has_source_change )); then select_task :common:check; fi
if ((${#selected[@]} > 0)); then printf '%s\n' "${!selected[@]}" | sort; fi
