#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
toolkit_dir="${1:-${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}}"
paths_file="${2:-}"
# shellcheck source=android_test_shards.sh
# shellcheck disable=SC1091
source "$root_dir/scripts/ci/android_test_shards.sh"
full_shards=("${ANDROID_TEST_SHARDS[@]}")

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
    xmsf/src/*|xmsf/build.gradle.kts|xmsf/proguard-rules.pro) select_task xmsf-compile ;;
    common/*|core/*|xmsf/*|xposed/*|manager/ui/*|manager/contract/*|manager/client/*|manager/port/*|settings/*|configuration/*|vendor/*|pinned/*)
      select_task android-pure-modules ;;
    mipush/*) select_task mipush-compile ;;
    *) has_source_change=1 ;;
  esac
done < <(sed -n '2,$p' "$paths_file")

if (( has_source_change )); then
  printf '%s\n' "${full_shards[@]}"
  exit 0
fi
if ((${#selected[@]} > 0)); then printf '%s\n' "${!selected[@]}" | sort; fi
