#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB_BIN="${ADB_BIN:-/home/linuxbrew/.linuxbrew/bin/adb}"
PKG="${PKG:-com.xiaomi.xmsf}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/out/device-regression}"
APK_PATH="${1:-}"

mkdir -p "$OUT_DIR"

if [[ ! -x "$ADB_BIN" ]]; then
  echo "adb not found: $ADB_BIN" >&2
  exit 1
fi

serial="${ANDROID_SERIAL:-}"
adb_cmd=("$ADB_BIN")
if [[ -n "$serial" ]]; then
  adb_cmd+=("-s" "$serial")
fi

device_count="$("${adb_cmd[@]}" devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
if [[ "$device_count" -lt 1 ]]; then
  echo "no adb device available" >&2
  exit 1
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
log_prefix="$OUT_DIR/$timestamp"

if [[ -n "$APK_PATH" ]]; then
  echo "installing $APK_PATH"
  "${adb_cmd[@]}" install -r -d "$APK_PATH"
fi

echo "device: $("${adb_cmd[@]}" get-serialno)"
echo "fingerprint: $("${adb_cmd[@]}" shell getprop ro.build.fingerprint | tr -d '\r')"

"${adb_cmd[@]}" logcat -c

echo "launching app and service"
"${adb_cmd[@]}" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
"${adb_cmd[@]}" shell am startservice -n "$PKG/com.xiaomi.xmsf.push.service.XMPushService" >/dev/null 2>&1 || \
  "${adb_cmd[@]}" shell am start-foreground-service -n "$PKG/com.xiaomi.xmsf.push.service.XMPushService" >/dev/null 2>&1 || true

sleep 8

echo "capturing dumpsys and runtime logs"
"${adb_cmd[@]}" shell dumpsys activity services "$PKG" > "${log_prefix}.dumpsys.txt" || true
"${adb_cmd[@]}" logcat -d -v threadtime \
  'PushHealthSnapshot:I' 'PushRuntime:D' 'XMPushService:D' 'MyLog:V' '*:S' > "${log_prefix}.logcat.txt" || true
"${adb_cmd[@]}" shell pm path "$PKG" > "${log_prefix}.pm-path.txt" || true
"${adb_cmd[@]}" shell dumpsys package "$PKG" > "${log_prefix}.package.txt" || true

echo "artifacts:"
echo "  ${log_prefix}.dumpsys.txt"
echo "  ${log_prefix}.logcat.txt"
echo "  ${log_prefix}.pm-path.txt"
echo "  ${log_prefix}.package.txt"
echo
echo "next manual checks:"
echo "  1. grep 'PushHealthSnapshot' ${log_prefix}.logcat.txt"
echo "  2. grep 'runtimeConnection=' ${log_prefix}.logcat.txt | tail"
echo "  3. install a target MiPush app and trigger registration, then rerun this script"
