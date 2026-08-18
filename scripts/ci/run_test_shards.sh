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

run_variant() {
  local variant="$1"
  shift
  local pattern
  for pattern in "$@"; do
    run_test ":xmsf:test${variant}DebugUnitTest" --tests "$pattern"
  done
}

run_shard() {
  case "$1" in
    app-compile) run_test :app:compileNormalDebugKotlin ;;
    mipush-compile) run_test :mipush:compileDebugKotlin ;;
    common-notification) run_test :common:testDebugUnitTest --tests 'io.github.magisk317.mipush.common.notification.*' ;;
    common-utils) run_test :common:testDebugUnitTest --tests 'io.github.magisk317.mipush.utils.*' --tests 'io.github.magisk317.mipush.common.utils.*' ;;
    common-other) run_test :common:testDebugUnitTest --tests 'io.github.magisk317.mipush.common.*' --tests 'io.github.magisk317.mipush.common.island.*' --tests 'io.github.magisk317.mipush.common.fakedevice.*' --tests 'io.github.magisk317.mipush.common.identity.*' --tests 'io.github.magisk317.mipush.common.configurations.*' --tests 'io.github.magisk317.mipush.common.process.*' --tests 'io.github.magisk317.mipush.common.utils.rom.*' ;;
    core) run_test :core:testDebugUnitTest --tests 'io.github.magisk317.mipush.*' --tests 'io.github.magisk317.mipush.notification.*' --tests 'io.github.magisk317.mipush.runtime.core.*' --tests 'io.github.magisk317.mipush.diagnostics.*' ;;
    xposed-systemui) run_test :xposed:testDebugUnitTest --tests 'io.github.magisk317.mipush.hook.systemui.*' ;;
    xposed-island) run_test :xposed:testDebugUnitTest --tests 'io.github.magisk317.mipush.hook.island.*' ;;
    xposed-other) run_test :xposed:testDebugUnitTest --tests 'io.github.magisk317.mipush.hook.system.*' --tests 'io.github.magisk317.mipush.hook.fakedevice.*' --tests 'io.github.magisk317.mipush.hook.fakedevice.compat.*' --tests 'io.github.magisk317.mipush.hook.securitycore.*' --tests 'io.github.magisk317.mipush.hook.keepalive.*' --tests 'io.github.magisk317.mipush.hook.amap.*' --tests 'io.github.magisk317.mipush.hook.xmsf.nm.*' --tests 'io.github.magisk317.mipush.hook.util.*' ;;
    manager-events) run_test :manager:testDebugUnitTest --tests 'io.github.magisk317.mipush.manager.events.*' ;;
    manager-main) run_test :manager:testDebugUnitTest --tests 'io.github.magisk317.mipush.main.*' --tests 'io.github.magisk317.mipush.feature.*' ;;
    manager-connection) run_test :manager:testDebugUnitTest --tests 'io.github.magisk317.mipush.manager.connection.*' ;;
    manager-other) run_test :manager:testDebugUnitTest --tests 'io.github.magisk317.mipush.manager.*' --tests 'io.github.magisk317.mipush.data.*' ;;
    xmsf-stock) run_variant Normal 'com.xiaomi.*'; run_variant Vc105 'com.xiaomi.*' ;;
    xmsf-notification) run_variant Normal 'io.github.magisk317.mipush.notification.*'; run_variant Vc105 'io.github.magisk317.mipush.notification.*' ;;
    xmsf-service) run_variant Normal 'io.github.magisk317.mipush.service.*' 'io.github.magisk317.mipush.push.*'; run_variant Vc105 'io.github.magisk317.mipush.service.*' 'io.github.magisk317.mipush.push.*' ;;
    xmsf-manager) run_variant Normal 'io.github.magisk317.mipush.manager.*'; run_variant Vc105 'io.github.magisk317.mipush.manager.*' ;;
    xmsf-runtime) run_variant Normal 'io.github.magisk317.mipush.runtime.*' 'io.github.magisk317.mipush.runtime.android.*' 'io.github.magisk317.mipush.app.*'; run_variant Vc105 'io.github.magisk317.mipush.runtime.*' 'io.github.magisk317.mipush.runtime.android.*' 'io.github.magisk317.mipush.app.*' ;;
    xmsf-other) run_variant Normal 'io.github.magisk317.mipush.utils.*' 'io.github.magisk317.mipush.platform.support.*' 'io.github.magisk317.mipush.diagnostics.*' 'io.github.magisk317.mipush.telemetry.*' 'io.github.magisk317.mipush.config.*' 'io.github.magisk317.mipush.compat.*' 'io.github.magisk317.mipush.common.utils.*' 'io.github.magisk317.mipush.control.*' 'io.github.magisk317.mipush.receiver.*' 'io.github.magisk317.mipush.runtime.store.db.*' 'io.github.magisk317.mipush.runtime.data.*'; run_variant Vc105 'io.github.magisk317.mipush.utils.*' 'io.github.magisk317.mipush.platform.support.*' 'io.github.magisk317.mipush.diagnostics.*' 'io.github.magisk317.mipush.telemetry.*' 'io.github.magisk317.mipush.config.*' 'io.github.magisk317.mipush.compat.*' 'io.github.magisk317.mipush.common.utils.*' 'io.github.magisk317.mipush.control.*' 'io.github.magisk317.mipush.receiver.*' 'io.github.magisk317.mipush.runtime.store.db.*' 'io.github.magisk317.mipush.runtime.data.*' ;;
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
