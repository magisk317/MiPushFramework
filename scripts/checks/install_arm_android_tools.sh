#!/usr/bin/env bash
set -euo pipefail

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" || ! -d "$sdk_root/build-tools" ]]; then
  echo "ERROR: Android SDK build-tools directory is unavailable: ${sdk_root:-<unset>}" >&2
  exit 1
fi

for tool in aidl aapt2; do
  source_tool="/usr/local/bin/$tool"
  if [[ ! -x "$source_tool" ]]; then
    echo "ERROR: ARM Android tool is unavailable: $source_tool" >&2
    exit 1
  fi
  if [[ "$(uname -m)" != "aarch64" ]]; then
    echo "ERROR: ARM Android tools can only be installed on aarch64 runners" >&2
    exit 1
  fi

  found=0
  for build_tools_dir in "$sdk_root"/build-tools/*; do
    [[ -d "$build_tools_dir" ]] || continue
    install -m 0755 "$source_tool" "$build_tools_dir/$tool"
    found=1
    echo "Using ARM $tool: $build_tools_dir/$tool"
  done
  if [[ "$found" -eq 0 ]]; then
    echo "ERROR: no Android build-tools installation exists under $sdk_root" >&2
    exit 1
  fi
done
