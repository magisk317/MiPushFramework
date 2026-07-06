#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

bash scripts/_toolkit/gradle/run_gradle_with_retry.sh :app:assembleRelease -PbuildSplits=true "$@"
