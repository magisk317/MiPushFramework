#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${1:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}}"
PLATFORMS_DIR="$SDK_ROOT/platforms"
PLATFORM_37_DIR="$PLATFORMS_DIR/android-37"
PLATFORM_37_0_DIR="$PLATFORMS_DIR/android-37.0"

mkdir -p "$PLATFORMS_DIR"

find_sdkmanager() {
  local candidates=(
    "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
    "$SDK_ROOT/cmdline-tools/bin/sdkmanager"
    "$SDK_ROOT/tools/bin/sdkmanager"
  )
  local candidate=""
  for candidate in "${candidates[@]}"; do
    if [[ -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  if command -v sdkmanager >/dev/null 2>&1; then
    command -v sdkmanager
    return 0
  fi
  return 1
}

install_platform_if_possible() {
  if [[ -d "$PLATFORM_37_DIR" || -d "$PLATFORM_37_0_DIR" ]]; then
    return 0
  fi

  local sdkmanager_bin=""
  if ! sdkmanager_bin="$(find_sdkmanager)"; then
    echo "sdkmanager not found; skipping explicit platform install"
    return 0
  fi

  if [[ ! -f "$SDK_ROOT/licenses/android-sdk-license" ]]; then
    echo "Android SDK licenses not found in $SDK_ROOT; skipping explicit platform install"
    return 0
  fi

  yes | "$sdkmanager_bin" --sdk_root="$SDK_ROOT" --install "platforms;android-37" || true
  yes | "$sdkmanager_bin" --sdk_root="$SDK_ROOT" --install "platforms;android-37.0" || true
}

ensure_alias() {
  if [[ -d "$PLATFORM_37_0_DIR" && ! -e "$PLATFORM_37_DIR" ]]; then
    ln -s "android-37.0" "$PLATFORM_37_DIR"
  fi
  if [[ -d "$PLATFORM_37_DIR" && ! -e "$PLATFORM_37_0_DIR" ]]; then
    ln -s "android-37" "$PLATFORM_37_0_DIR"
  fi
}

install_platform_if_possible
ensure_alias

if [[ ! -d "$PLATFORM_37_DIR" && ! -L "$PLATFORM_37_DIR" ]]; then
  echo "Missing Android SDK platform alias: $PLATFORM_37_DIR" >&2
  exit 1
fi

if [[ ! -d "$PLATFORM_37_0_DIR" && ! -L "$PLATFORM_37_0_DIR" ]]; then
  echo "Missing Android SDK platform alias: $PLATFORM_37_0_DIR" >&2
  exit 1
fi

echo "Android SDK platform aliases ready:"
ls -ld "$PLATFORM_37_DIR" "$PLATFORM_37_0_DIR"
