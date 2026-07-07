#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"
TOOLKIT_SCRIPT="${TOOLKIT_DIR}/release/check_release_guard.sh"

# MiPushFramework configuration
TAG_NAME="${1:-}"

source "${TOOLKIT_SCRIPT}"
check_release_guard "$ROOT_DIR" "$TAG_NAME"
"${ROOT_DIR}/scripts/check_zygisk_version_sync.sh" "$ROOT_DIR"
