#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

bash scripts/ensure_android_sdk_platform_alias.sh

SOURCE_PATHS=(
  "common/src/main/java/com/magisk317"
  "common/src/main/java/top/trumeet"
  "runtime-core/src/main/java"
  "push/src/main/java/com/magisk317"
  "push/src/main/java/top/trumeet"
)

search_source() {
  local pattern="$1"
  local output="$2"
  if command -v rg >/dev/null 2>&1; then
    rg -n "$pattern" "${SOURCE_PATHS[@]}" --glob '*.kt' --glob '*.java' >"${output}"
  else
    grep -RInE --include='*.kt' --include='*.java' "$pattern" "${SOURCE_PATHS[@]}" >"${output}"
  fi
}

search_file() {
  local pattern="$1"
  local input="$2"
  local output="$3"
  if command -v rg >/dev/null 2>&1; then
    rg -n -i "$pattern" "${input}" >"${output}"
  else
    grep -Ein "$pattern" "${input}" >"${output}"
  fi
}

echo "[check] scanning source for deprecated annotations/suppressions"
if search_source '@Suppress\([^)]*DEPRECATION|@SuppressWarnings\([^)]*deprecation|@Deprecated\(' /tmp/mipush_deprecation_symbols.log; then
  cat /tmp/mipush_deprecation_symbols.log
  echo "[fail] deprecated markers are still present"
  exit 1
fi

LOG_FILE=/tmp/mipush_deprecation_build.log
echo "[check] compiling androidTest kotlin with --warning-mode all"
bash scripts/with_workspace_gradle_lock.sh \
  -I gradle/security-overrides.init.gradle \
  :push:clean \
  :push:compileDebugAndroidTestKotlin \
  --warning-mode all \
  -Pkotlin.incremental=false \
  >"${LOG_FILE}" 2>&1

if search_file '(^w: .*deprecated|^w: .*deprecation|^warning: .*deprecated|^warning: .*deprecation)' "${LOG_FILE}" /tmp/mipush_deprecation_warnings.log; then
  cat /tmp/mipush_deprecation_warnings.log
  echo "[fail] deprecation warnings detected in build log"
  exit 1
fi

echo "[pass] zero deprecation markers and warnings"
