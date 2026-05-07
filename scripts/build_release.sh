#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

bash "${ROOT_DIR}/scripts/with_workspace_gradle_lock.sh" :xmsf:assembleRelease -PbuildSplits=true "$@"
