#!/usr/bin/env bash
# Regression test for the ci-dist staging script.
#
# The bug this pins down: stage_ci_dist.sh runs in after_script, and GitLab runs
# after_script in its own shell. The first wiring read only MAGISK_VERSION_CODE,
# exported by the build script, and an export does not cross into after_script --
# every staged manifest vanished while the job reported success. The stager now
# prefers the env var when a runner happens to carry it and otherwise falls back
# to the toolkit helper reading the workspace file, so this test runs the stager
# the way CI does (no inherited environment) and asserts the manifest carries the
# allocated code that came through that file.
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
stager="$root_dir/scripts/ci/stage_ci_dist.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

# Workspace the stager expects: the two universal APK names it globs, plus the
# version catalog the xmsf manifest reads pushVersionCode from.
mkdir -p "$tmp_dir/work/mipush/build/outputs/apk/github/debug"
mkdir -p "$tmp_dir/work/xmsf/build/outputs/apk/normal/debug"
mkdir -p "$tmp_dir/work/gradle"
printf 'apk' > "$tmp_dir/work/mipush/build/outputs/apk/github/debug/universal_mipush_v0.4.1_debug.apk"
printf 'apk' > "$tmp_dir/work/xmsf/build/outputs/apk/normal/debug/universal_xiaomi_xmsf_v1.2.3_debug.apk"
cat > "$tmp_dir/work/gradle/libs.versions.toml" <<'TOML'
[versions]
pushVersionCode = "1003003000"
TOML

# Prefer the real helper (CI resolves the pinned toolkit into .magisk-ci-toolkit);
# fall back to a stub that mirrors its contract when it is not around, so the
# test stays runnable on a developer machine.
mkdir -p "$tmp_dir/toolkit/ci"
real_helper="${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}/ci/allocated_version_code.sh"
if [ -f "$real_helper" ]; then
  cp "$real_helper" "$tmp_dir/toolkit/ci/allocated_version_code.sh"
else
  cat > "$tmp_dir/toolkit/ci/allocated_version_code.sh" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
code="${MAGISK_VERSION_CODE:-}"
file="${MAGISK_VERSION_CODE_FILE:-$CI_PROJECT_DIR/.magisk-version-code}"
if [ -z "$code" ] && [ -f "$file" ]; then
  code="$(tr -d '[:space:]' < "$file")"
fi
if [ -z "$code" ]; then
  echo "no allocated versionCode available" >&2
  exit 1
fi
printf '%s\n' "$code"
STUB
fi
chmod +x "$tmp_dir/toolkit/ci/allocated_version_code.sh"

run_stager() {
  env -u MAGISK_VERSION_CODE -u MAGISK_VERSION_CODE_FILE \
    CI_PROJECT_DIR="$tmp_dir/work" \
    MAGISK_CI_TOOLKIT_DIR="$tmp_dir/toolkit" \
    CI_PIPELINE_IID=777 \
    bash "$stager"
}

# --- happy path: the workspace handoff carries the allocated code ---------------
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
run_stager

manifest="$tmp_dir/work/ci-dist/io.github.magisk317.mipush/0.4.1/mipush-ci-manifest.json"
[ -f "$manifest" ] || { echo "FAIL: mipush manifest was not staged" >&2; exit 1; }
grep -Fq '"versionCode": 1623' "$manifest" || {
  echo "FAIL: mipush manifest does not carry the allocated code:" >&2
  cat "$manifest" >&2
  exit 1
}
grep -Fq '"minSupportedVersionCode": 1623' "$manifest" || {
  echo "FAIL: minSupportedVersionCode does not carry the allocated code:" >&2
  cat "$manifest" >&2
  exit 1
}
grep -Fq '"versionName": "0.4.1"' "$manifest" || {
  echo "FAIL: versionName was not parsed from the APK file name" >&2
  exit 1
}

xmsf_manifest="$tmp_dir/work/ci-dist/com.xiaomi.xmsf/1.2.3/xmsf-runtime-manifest.json"
[ -f "$xmsf_manifest" ] || { echo "FAIL: xmsf manifest was not staged" >&2; exit 1; }
grep -Fq '"versionCode": 1003003000' "$xmsf_manifest" || {
  echo "FAIL: xmsf manifest does not carry pushVersionCode from the catalog" >&2
  exit 1
}
grep -Fq '"minManagerVersionCode": 1623' "$xmsf_manifest" || {
  echo "FAIL: minManagerVersionCode does not carry the allocated manager versionCode" >&2
  exit 1
}

# --- precedence path: an exported code wins over the workspace file -------------
# after_script normally sees neither, but when a runner does carry the export the
# stager must use it instead of a file left over from an earlier attempt.
rm -rf "$tmp_dir/work/ci-dist"
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
env -u MAGISK_VERSION_CODE_FILE \
  MAGISK_VERSION_CODE=9999 \
  CI_PROJECT_DIR="$tmp_dir/work" \
  MAGISK_CI_TOOLKIT_DIR="$tmp_dir/toolkit" \
  CI_PIPELINE_IID=777 \
  bash "$stager"
grep -Fq '"versionCode": 9999' "$manifest" || {
  echo "FAIL: the exported MAGISK_VERSION_CODE was not preferred over the file:" >&2
  cat "$manifest" >&2
  exit 1
}

# --- failure path: no code anywhere must fail loudly, not stage a stale one -----
rm -rf "$tmp_dir/work/ci-dist" "$tmp_dir/work/.magisk-version-code"
if run_stager >/dev/null 2>&1; then
  echo "FAIL: stager succeeded with no allocated versionCode available" >&2
  exit 1
fi
[ ! -e "$tmp_dir/work/ci-dist/io.github.magisk317.mipush/0.4.1/mipush-ci-manifest.json" ] || {
  echo "FAIL: a manifest was staged from an unknown versionCode" >&2
  exit 1
}

# --- failure path: an unreadable pushVersionCode must fail the stager ----------
# The xmsf manifest's own versionCode comes from the catalog; staging an empty
# readout would produce invalid JSON that every in-app probe fails to parse.
rm -rf "$tmp_dir/work/ci-dist"
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
grep -v 'pushVersionCode' "$tmp_dir/work/gradle/libs.versions.toml" > "$tmp_dir/work/gradle/libs.versions.toml.cut"
mv "$tmp_dir/work/gradle/libs.versions.toml.cut" "$tmp_dir/work/gradle/libs.versions.toml"
if run_stager >/dev/null 2>&1; then
  echo "FAIL: stager succeeded with an unreadable pushVersionCode" >&2
  exit 1
fi
[ ! -e "$tmp_dir/work/ci-dist/com.xiaomi.xmsf/1.2.3/xmsf-runtime-manifest.json" ] || {
  echo "FAIL: an xmsf manifest was staged from an unreadable pushVersionCode" >&2
  exit 1
}

echo "OK: stage_ci_dist reads the allocated code through the toolkit helper"
