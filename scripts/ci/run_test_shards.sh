#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
toolkit_dir="${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}"
gradle_runner="$toolkit_dir/gradle/run_gradle_with_retry.sh"

if [[ ! -x "$gradle_runner" ]]; then
  echo "ERROR: Gradle runner is unavailable: $gradle_runner" >&2
  exit 2
fi

# Keep shared Gradle options in one place. CI supplies --warning-mode through
# MAGISK_GRADLE_ARGS; putting it in common_args as well makes Gradle reject the
# invocation as a duplicate option. The default keeps local shard runs useful.
common_args=(-PminifyDebug -Pkotlin.incremental=false -x detekt -x qualityGateDetekt -x verifyModuleBoundaries)
read -r -a gradle_env_args <<< "${MAGISK_GRADLE_ARGS:---warning-mode all}"

run_test() {
  local task="$1"
  shift
  echo "== test shard: $task $* =="
  bash "$gradle_runner" "${gradle_env_args[@]}" "${common_args[@]}" "$task" "$@"
}

run_shard() {
  case "$1" in
    app-compile) run_test :app:compileNormalDebugKotlin ;;
    mipush-compile) run_test :mipush:compileDebugKotlin ;;
    android-pure-modules)
      run_test :build-logic:test
      run_test \
        :magisk-ui-kit:testDebugUnitTest \
        :magisk-ui-kit:billing:testDebugUnitTest \
        :magisk-xposed-kit:testDebugUnitTest \
        :magisk-xposed-kit:diagnostics:testDebugUnitTest \
        :magisk-xposed-kit:logging:testDebugUnitTest \
        :mipush:testGithubDebugUnitTest \
        :core:testDebugUnitTest \
        :manager:ui:testDebugUnitTest
      run_test :configuration:testDebugUnitTest
      run_test :manager:contract:testDebugUnitTest
      run_test :manager:client:testDebugUnitTest
      run_test :common:testDebugUnitTest
      run_test :xposed:testDebugUnitTest
      run_test :xmsf:runtime:testDebugUnitTest
      run_test :xmsf:shell:testNormalDebugUnitTest
      ;;
    *) echo "ERROR: unknown test shard: $1" >&2; exit 2 ;;
  esac
}

if (($# == 0)); then
  echo "ERROR: at least one test shard is required" >&2
  exit 2
fi

cd "$root_dir"
for shard in "$@"; do
  run_shard "$shard"
done
