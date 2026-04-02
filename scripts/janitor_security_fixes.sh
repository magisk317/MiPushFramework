#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

echo "Syncing security overrides from open Dependabot alerts..."
chmod +x scripts/sync_security_overrides.sh
scripts/sync_security_overrides.sh

if git diff --quiet -- gradle/security-overrides.properties gradle/security-overrides.init.gradle; then
  echo "No override changes generated. Skipping build validation."
  exit 0
fi

echo "Override files changed. Running build/test validation before opening PR..."
chmod +x ./gradlew
./gradlew --no-daemon \
  -I gradle/security-overrides.init.gradle \
  --warning-mode all \
  :push:assembleDebug \
  :push:testDebugUnitTest \
  -PbuildSplits \
  -Pkotlin.incremental=false

echo "Janitor validation passed."
