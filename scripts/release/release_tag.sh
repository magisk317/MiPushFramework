#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"

# The branch snapshot, the signed tag and the force-push protection dance all
# live in the toolkit now. Only the project-specific pre-push build stays here.
export MAGISK_RELEASE_GUARD_SCRIPT="${ROOT_DIR}/scripts/release/check_release_guard.sh"
# shellcheck source=release_tag.sh
source "${TOOLKIT_DIR}/release/release_tag.sh"

run_pre_push_checks() {
  local gradle_args=(
    --warning-mode all
    :common:compileDebugKotlin
    :xmsf:runtime:compileDebugKotlin
    :xmsf:shell:assembleNormalDebug
    :xmsf:shell:assembleVc105Debug
    :xmsf:assembleRelease
    :mipush:assembleRelease
    -PbuildSplits
    -Pkotlin.incremental=false
  )

  if release_skip_detekt; then
    echo "WARN: RELEASE_TAG_SKIP_DETEKT is enabled; detekt tasks will be skipped."
    gradle_args+=(-x detekt -x qualityGateDetekt)
  else
    echo "Detekt checks are blocking. Set RELEASE_TAG_SKIP_DETEKT=1 to bypass them."
  fi

  echo "Running pre-push CI command..."
  (
    cd "$ROOT_DIR"
    bash "$TOOLKIT_DIR/gradle/run_gradle_with_retry.sh" "${gradle_args[@]}"
    bash "$ROOT_DIR/scripts/ci/tests/test_run_test_shards.sh"
    mapfile -t selected_shards < <(
      python3 "$TOOLKIT_DIR/ci/select_gradle_tasks.py" \
        --root "$ROOT_DIR" \
        --toolkit "$TOOLKIT_DIR" \
        --config "$ROOT_DIR/scripts/config/android-test-tasks.json"
    )
    MAGISK_CI_TOOLKIT_DIR="$TOOLKIT_DIR" bash "$ROOT_DIR/scripts/ci/run_test_shards.sh" \
      "${selected_shards[@]}"
  )
  echo "Pre-push checks passed."
}

release_tag "$ROOT_DIR"
