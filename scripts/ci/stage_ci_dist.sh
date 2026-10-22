#!/usr/bin/env bash
# Stages the ci-dist artifact layout consumed by the in-app update checks.
#
#   ci-dist/<packageName>/version.txt                    plain-text version sidecar
#   ci-dist/<packageName>/<versionName>/<apk>            version-keyed payload
#   ci-dist/<packageName>/<versionName>/<apk>.sha256     checksum sidecar
#   ci-dist/<packageName>/<versionName>/<manifest>       UpdateManifest JSON
#
# Both packages share one layout. The manager's OTA probe
# (GitLabCiArtifactUpdateSource) and the XMSF runtime probe
# (XmsfCiArtifactProbe) resolve the <versionName> segment from their sidecar
# and verify the sha256 before anything installs, so a staged set must always
# travel complete: APK + .sha256 + manifest.
#
# Version gates ride the pipeline iid — the same axis the manager APK's
# versionCode uses (see -PversionCode=$CI_PIPELINE_IID) — so old managers
# never pull a build they cannot run.
#
# Called from android:build's after_script; kept out of the YAML so the
# heredocs below stay valid shell (YAML block scalars cannot host a
# top-level heredoc terminator).
set -euo pipefail

cd "$CI_PROJECT_DIR"

# ---------------------------------------------------------------------------
# Manager APK: io.github.magisk317.mipush/<versionName>/mipush-ci.apk
# ---------------------------------------------------------------------------
mipush_apk=$(ls mipush/build/outputs/apk/github/debug/universal_*.apk | tail -n 1)
mipush_version=$(basename "$mipush_apk" | sed -n 's/^universal_.*_v\(.*\)_debug\.apk$/\1/p')
mipush_dir="ci-dist/io.github.magisk317.mipush/$mipush_version"
mkdir -p "$mipush_dir"
cp "$mipush_apk" "$mipush_dir/mipush-ci.apk"
mipush_sha=$(sha256sum "$mipush_dir/mipush-ci.apk" | cut -d' ' -f1)
printf '%s  mipush-ci.apk\n' "$mipush_sha" > "$mipush_dir/mipush-ci.apk.sha256"
printf '%s' "$mipush_version" > ci-dist/io.github.magisk317.mipush/version.txt
cat > "$mipush_dir/mipush-ci-manifest.json" <<EOF
{
  "schema": 1,
  "variant": "github",
  "versionName": "$mipush_version",
  "versionCode": $CI_PIPELINE_IID,
  "sha256": "$mipush_sha",
  "force": true,
  "minSupportedVersionCode": $CI_PIPELINE_IID
}
EOF

# ---------------------------------------------------------------------------
# XMSF runtime APK: com.xiaomi.xmsf/<versionName>/xmsf-runtime.apk
#
# Keyed by the runtime's own package name and versionName — the same
# two-level shape the generic package registry uses, so the in-app downloader
# only swaps its base URL and keeps its whole verification chain (manifest
# gate -> sha256 sidecar -> same-signature check).
# ---------------------------------------------------------------------------
xmsf_apk=$(ls xmsf/build/outputs/apk/github/debug/universal_*.apk | tail -n 1)
xmsf_version=$(basename "$xmsf_apk" | sed -n 's/^universal_.*_xmsf_v\(.*\)_debug\.apk$/\1/p')
xmsf_dir="ci-dist/com.xiaomi.xmsf/$xmsf_version"
mkdir -p "$xmsf_dir"
cp "$xmsf_apk" "$xmsf_dir/xmsf-runtime.apk"
xmsf_sha=$(sha256sum "$xmsf_dir/xmsf-runtime.apk" | cut -d' ' -f1)
printf '%s  xmsf-runtime.apk\n' "$xmsf_sha" > "$xmsf_dir/xmsf-runtime.apk.sha256"
printf '%s' "$xmsf_version" > ci-dist/com.xiaomi.xmsf/version.txt
cat > "$xmsf_dir/xmsf-runtime-manifest.json" <<EOF
{
  "schema": 1,
  "variant": "github",
  "versionName": "$xmsf_version",
  "versionCode": $(sed -nE 's/^pushVersionCode = "([0-9]+)"$/\1/p' gradle/libs.versions.toml),
  "minManagerVersionCode": $CI_PIPELINE_IID,
  "sha256": "$xmsf_sha"
}
EOF
