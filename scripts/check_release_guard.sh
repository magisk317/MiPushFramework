#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TOOLKIT_SCRIPT="${ROOT_DIR}/scripts/_toolkit/release/check_release_guard.sh"

# MiPushFramework configuration
TAG_NAME="${1:-}"

source "${TOOLKIT_SCRIPT}"
check_release_guard "$ROOT_DIR" "$TAG_NAME"
