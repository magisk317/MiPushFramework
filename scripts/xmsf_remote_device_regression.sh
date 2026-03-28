#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SSH_JUMP="${SSH_JUMP:-john@100.79.25.74}"
SSH_TARGET="${SSH_TARGET:-user@100.93.82.82}"
REMOTE_ADB_BIN="${REMOTE_ADB_BIN:-/home/linuxbrew/.linuxbrew/bin/adb}"
PKG="${PKG:-com.xiaomi.xmsf}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/out/remote-device-regression}"

mkdir -p "$OUT_DIR"
timestamp="$(date +%Y%m%d-%H%M%S)"
log_prefix="$OUT_DIR/$timestamp"

remote() {
  ssh -J "$SSH_JUMP" "$SSH_TARGET" "$@"
}

echo "probing remote adb devices"
remote "$REMOTE_ADB_BIN" devices | tee "${log_prefix}.devices.txt"

if ! grep -q $'\tdevice$' "${log_prefix}.devices.txt"; then
  echo "no remote adb device online" >&2
  exit 2
fi

remote "$REMOTE_ADB_BIN" logcat -c || true
remote "$REMOTE_ADB_BIN" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
remote "$REMOTE_ADB_BIN" shell am startservice -n "$PKG/com.xiaomi.xmsf.push.service.XMPushService" >/dev/null 2>&1 || \
  remote "$REMOTE_ADB_BIN" shell am start-foreground-service -n "$PKG/com.xiaomi.xmsf.push.service.XMPushService" >/dev/null 2>&1 || true

sleep 8

remote "$REMOTE_ADB_BIN" shell dumpsys activity services "$PKG" > "${log_prefix}.dumpsys.txt" || true
remote "$REMOTE_ADB_BIN" logcat -d -v threadtime | \
  grep -E 'PushHealthSnapshot|PushRuntime|XMPushService|MyLog' > "${log_prefix}.logcat.txt" || true

echo "artifacts:"
echo "  ${log_prefix}.devices.txt"
echo "  ${log_prefix}.dumpsys.txt"
echo "  ${log_prefix}.logcat.txt"
