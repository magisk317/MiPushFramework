#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[check] scanning source for deprecated annotations/suppressions"
if rg -n 'Suppress\([^)]*DEPRECATION|@Deprecated\(' --glob '*.kt' --glob '*.java' > /tmp/mipush_deprecation_symbols.log; then
  cat /tmp/mipush_deprecation_symbols.log
  echo "[fail] deprecated markers are still present"
  exit 1
fi

LOG_FILE=/tmp/mipush_deprecation_build.log
echo "[check] compiling androidTest kotlin with --warning-mode all"
./gradlew :push:clean :push:compileNormalDebugAndroidTestKotlin --warning-mode all >"${LOG_FILE}" 2>&1

if rg -n '(?i)deprecated|deprecation' "${LOG_FILE}" >/tmp/mipush_deprecation_warnings.log; then
  cat /tmp/mipush_deprecation_warnings.log
  echo "[fail] deprecation warnings detected in build log"
  exit 1
fi

echo "[pass] zero deprecation markers and warnings"
