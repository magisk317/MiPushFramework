#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"

bash "$TOOLKIT_DIR/gradle/run_gradle_with_retry.sh" \
  --no-configuration-cache \
  :magisk-ui-kit:compileDebugKotlin \
  :magisk-xposed-kit:compileDebugKotlin \
  :manager:compileDebugKotlin \
  :app:compileNormalDebugKotlin \
  :mipush:compileGithubDebugKotlin
