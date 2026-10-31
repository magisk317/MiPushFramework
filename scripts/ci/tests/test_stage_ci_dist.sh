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
printf 'apk' > "$tmp_dir/work/mipush/build/outputs/apk/github/debug/universal_mipush_v0.4.1-20261027_070707_debug.apk"
printf 'apk' > "$tmp_dir/work/xmsf/build/outputs/apk/normal/debug/universal_xiaomi_xmsf_v1.2.3-20261027_070707_debug.apk"
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
  env -u MAGISK_VERSION_CODE -u MAGISK_VERSION_CODE_FILE -u CI_COMMIT_SHORT_SHA \
    -u CI_COMMIT_REF_NAME \
    CI_PROJECT_DIR="$tmp_dir/work" \
    MAGISK_CI_TOOLKIT_DIR="$tmp_dir/toolkit" \
    CI_PIPELINE_IID=777 \
    bash "$stager"
}

# --- happy path: the workspace handoff carries the allocated code ---------------
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
run_stager

manifest="$tmp_dir/work/ci-dist/io.github.magisk317.mipush/0.4.1-20261027_070707/mipush-ci-manifest.json"
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
grep -Fq '"versionName": "0.4.1-20261027_070707"' "$manifest" || {
  echo "FAIL: versionName was not parsed from the APK file name" >&2
  exit 1
}

xmsf_manifest="$tmp_dir/work/ci-dist/com.xiaomi.xmsf/1.2.3-20261027_070707/xmsf-runtime-manifest.json"
[ -f "$xmsf_manifest" ] || { echo "FAIL: xmsf manifest was not staged" >&2; exit 1; }
grep -Fq '"versionCode": 1003003000' "$xmsf_manifest" || {
  echo "FAIL: xmsf manifest does not carry pushVersionCode from the catalog" >&2
  exit 1
}
grep -Fq '"minManagerVersionCode": 1623' "$xmsf_manifest" || {
  echo "FAIL: minManagerVersionCode does not carry the allocated manager versionCode" >&2
  exit 1
}

# --- optional variant missing: a skipped channel must not abort the stager -----
# vc105 is staged best-effort, and a workspace that built only the normal
# variant (this fixture, or a build with the flavor disabled) must still stage
# normal and exit 0. find exits non-zero on a missing directory, which pipefail
# turns into a silent abort of the whole stager.
rm -rf "$tmp_dir/work/ci-dist"
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
if ! run_stager > "$tmp_dir/skip.log" 2>&1; then
  echo "FAIL: stager aborted when the vc105 runtime APK was absent:" >&2
  cat "$tmp_dir/skip.log" >&2
  exit 1
fi
grep -Fq "WARN: no vc105 runtime APK" "$tmp_dir/skip.log" || {
  echo "FAIL: the skipped vc105 channel was not reported:" >&2
  cat "$tmp_dir/skip.log" >&2
  exit 1
}
[ -f "$xmsf_manifest" ] || {
  echo "FAIL: normal runtime was not staged when vc105 was absent" >&2
  exit 1
}
[ ! -e "$tmp_dir/work/ci-dist/com.xiaomi.xmsf-vc105/1.2.3-20261027_070707/xmsf-runtime-manifest.json" ] || {
  echo "FAIL: a vc105 payload was staged with no vc105 APK" >&2
  exit 1
}

# --- required variant missing: no normal runtime APK must fail the stager ------
mv "$tmp_dir/work/xmsf/build/outputs/apk/normal/debug/universal_xiaomi_xmsf_v1.2.3-20261027_070707_debug.apk" \
  "$tmp_dir/normal-apk.bak"
rm -rf "$tmp_dir/work/ci-dist"
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
if run_stager >/dev/null 2>&1; then
  echo "FAIL: stager succeeded with no normal runtime APK" >&2
  exit 1
fi
mv "$tmp_dir/normal-apk.bak" \
  "$tmp_dir/work/xmsf/build/outputs/apk/normal/debug/universal_xiaomi_xmsf_v1.2.3-20261027_070707_debug.apk"

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

# --- the runtime manifest declares the commit it was built from ----------------
# Diagnostic only, so an unknown commit stages as an empty string instead of
# failing the job; a workspace that is not a git checkout (this fixture) yields
# that empty value, and CI's exported short sha wins when it is present.
rm -rf "$tmp_dir/work/ci-dist"
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
run_stager >/dev/null
grep -Fq '"commit": ""' "$xmsf_manifest" || {
  echo "FAIL: a workspace without a commit must stage an empty one:" >&2
  cat "$xmsf_manifest" >&2
  exit 1
}
env -u MAGISK_VERSION_CODE_FILE \
  CI_COMMIT_SHORT_SHA=7865a560 \
  CI_PROJECT_DIR="$tmp_dir/work" \
  MAGISK_CI_TOOLKIT_DIR="$tmp_dir/toolkit" \
  CI_PIPELINE_IID=777 \
  bash "$stager" >/dev/null
grep -Fq '"commit": "7865a560"' "$xmsf_manifest" || {
  echo "FAIL: CI_COMMIT_SHORT_SHA was not declared in the runtime manifest:" >&2
  cat "$xmsf_manifest" >&2
  exit 1
}
grep -Fq '"commit": "7865a560"' "$tmp_dir/work/ci-dist/com.xiaomi.xmsf/1.2.3-20261027_070707/xmsf-runtime-manifest.json" || {
  echo "FAIL: the declared commit was written to the wrong channel root" >&2
  exit 1
}

# --- the manager manifest declares the provenance the prompt localizes ---------
# ui-kit carries branch and commit as their own UpdateManifest fields and renders
# them as a localized CI note, so a manager manifest without them silently loses
# that line. Unresolvable values stage as empty, the same rule the runtime commit
# already follows.
rm -rf "$tmp_dir/work/ci-dist"
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
run_stager >/dev/null
grep -Fq '"branch": ""' "$manifest" || {
  echo "FAIL: a workspace without a branch must stage an empty one:" >&2
  cat "$manifest" >&2
  exit 1
}
grep -Fq '"commit": ""' "$manifest" || {
  echo "FAIL: a workspace without a commit must stage an empty one:" >&2
  cat "$manifest" >&2
  exit 1
}
env -u MAGISK_VERSION_CODE_FILE \
  CI_COMMIT_REF_NAME=beta CI_COMMIT_SHORT_SHA=7865a560 \
  CI_PROJECT_DIR="$tmp_dir/work" \
  MAGISK_CI_TOOLKIT_DIR="$tmp_dir/toolkit" \
  CI_PIPELINE_IID=777 \
  bash "$stager" >/dev/null
grep -Fq '"branch": "beta"' "$manifest" || {
  echo "FAIL: CI_COMMIT_REF_NAME was not declared in the manager manifest:" >&2
  cat "$manifest" >&2
  exit 1
}
grep -Fq '"commit": "7865a560"' "$manifest" || {
  echo "FAIL: CI_COMMIT_SHORT_SHA was not declared in the manager manifest:" >&2
  cat "$manifest" >&2
  exit 1
}

# --- failure path: a version without the build timestamp must be refused -------
# The stage directory is named after this value, and ui-kit resolves a
# version-keyed artifact path only from a sidecar that carries the timestamp
# (AppUpdateChecker.kt, ciSidecarVersionShape), so a bare version would publish a
# path no client can name and an update channel that reports "up to date".
manager_dir="$tmp_dir/work/mipush/build/outputs/apk/github/debug"
manager_stamped="$manager_dir/universal_mipush_v0.4.1-20261027_070707_debug.apk"
manager_bare="$manager_dir/universal_mipush_v0.4.1_debug.apk"
mv "$manager_stamped" "$tmp_dir/manager-apk.bak"
printf 'apk' > "$manager_bare"
rm -rf "$tmp_dir/work/ci-dist"
printf '1623\n' > "$tmp_dir/work/.magisk-version-code"
if run_stager > "$tmp_dir/nostamp.log" 2>&1; then
  echo "FAIL: the stager accepted a manager versionName without a build timestamp" >&2
  cat "$tmp_dir/nostamp.log" >&2
  exit 1
fi
grep -Fq 'cannot resolve versionName from the manager APK name' "$tmp_dir/nostamp.log" || {
  echo "FAIL: the timestamp-less manager versionName was not reported:" >&2
  cat "$tmp_dir/nostamp.log" >&2
  exit 1
}
[ -z "$(find "$tmp_dir/work/ci-dist" -mindepth 1 -print 2>/dev/null | head -1)" ] || {
  echo "FAIL: something was staged despite the unresolvable manager version" >&2
  exit 1
}
rm "$manager_bare"
mv "$tmp_dir/manager-apk.bak" "$manager_stamped"

# The same gate covers the runtime channel: a variant whose name cannot be
# resolved must fail the job instead of quietly skipping the whole channel.
normal_dir="$tmp_dir/work/xmsf/build/outputs/apk/normal/debug"
normal_stamped="$normal_dir/universal_xiaomi_xmsf_v1.2.3-20261027_070707_debug.apk"
normal_bare="$normal_dir/universal_xiaomi_xmsf_v1.2.3_debug.apk"
mv "$normal_stamped" "$tmp_dir/normal-apk.bak"
printf 'apk' > "$normal_bare"
rm -rf "$tmp_dir/work/ci-dist"
if run_stager > "$tmp_dir/nostamp2.log" 2>&1; then
  echo "FAIL: the stager accepted a runtime versionName without a build timestamp" >&2
  cat "$tmp_dir/nostamp2.log" >&2
  exit 1
fi
grep -Fq 'cannot resolve versionName from the runtime APK name' "$tmp_dir/nostamp2.log" || {
  echo "FAIL: the timestamp-less runtime versionName was not reported:" >&2
  cat "$tmp_dir/nostamp2.log" >&2
  exit 1
}
rm "$normal_bare"
mv "$tmp_dir/normal-apk.bak" "$normal_stamped"

# --- failure path: no code anywhere must fail loudly, not stage a stale one -----
rm -rf "$tmp_dir/work/ci-dist" "$tmp_dir/work/.magisk-version-code"
if run_stager >/dev/null 2>&1; then
  echo "FAIL: stager succeeded with no allocated versionCode available" >&2
  exit 1
fi
[ ! -e "$tmp_dir/work/ci-dist/io.github.magisk317.mipush/0.4.1-20261027_070707/mipush-ci-manifest.json" ] || {
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
[ ! -e "$tmp_dir/work/ci-dist/com.xiaomi.xmsf/1.2.3-20261027_070707/xmsf-runtime-manifest.json" ] || {
  echo "FAIL: an xmsf manifest was staged from an unreadable pushVersionCode" >&2
  exit 1
}

echo "OK: stage_ci_dist reads the allocated code through the toolkit helper"
