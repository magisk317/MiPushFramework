#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLKIT_SCRIPT="${SCRIPT_DIR}/_toolkit/release/check_release_guard.sh"

# MiPushFramework configuration
export MAGISK_ROOT_DEPTH=2

exec bash "${TOOLKIT_SCRIPT}" "$@"
