#!/usr/bin/env bash
set -euo pipefail

SSH_JUMP="${SSH_JUMP:-john@100.79.25.74}"
SSH_TARGET="${SSH_TARGET:-user@100.93.82.82}"
REMOTE_ADB_BIN="${REMOTE_ADB_BIN:-/home/linuxbrew/.linuxbrew/bin/adb}"

if [[ "$#" -lt 1 ]]; then
  echo "usage: $0 <adb-args...>" >&2
  exit 1
fi

ssh -J "$SSH_JUMP" "$SSH_TARGET" "$REMOTE_ADB_BIN" "$@"
