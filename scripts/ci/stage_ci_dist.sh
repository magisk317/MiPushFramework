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
# The runtime builds under its own "version" flavor dimension (normal/vc105);
# CI assembles every flavor, so pin the one the registry publishes (normal,
# see scripts/ci/xmsf_runtime_publish.sh) instead of guessing a directory.
# after_script failures cannot fail the job, so exit loudly on a missing APK
# rather than staging a half-empty ci-dist silently.
xmsf_apk=$(find xmsf/build/outputs/apk/normal/debug -type f -name 'universal_*_xmsf_v*_debug.apk' | sort | tail -n 1)
if [ -z "$xmsf_apk" ]; then
  echo "ERROR: no universal xmsf debug APK under xmsf/build/outputs/apk/normal/debug" >&2
  exit 1
fi
xmsf_version=$(basename "$xmsf_apk" | sed -n 's/^universal_.*_xmsf_v\(.*\)_debug\.apk$/\1/p')
xmsf_dir="ci-dist/com.xiaomi.xmsf/$xmsf_version"
mkdir -p "$xmsf_dir"
cp "$xmsf_apk" "$xmsf_dir/xmsf-runtime.apk"
xmsf_sha=$(sha256sum "$xmsf_dir/xmsf-runtime.apk" | cut -d' ' -f1)
printf '%s  xmsf-runtime.apk\n' "$xmsf_sha" > "$xmsf_dir/xmsf-runtime.apk.sha256"
printf '%s' "$xmsf_version" > ci-dist/com.xiaomi.xmsf/version.txt
# The runtime versionCode comes from the catalog's pushVersionCode — a different
# scale than the manager's version axis. An unreadable readout would stage
# invalid JSON both probes choke on, so fail loudly naming the source instead.
xmsf_version_code="$(sed -nE 's/^pushVersionCode = "([0-9]+)"$/\1/p' gradle/libs.versions.toml)"
: "${xmsf_version_code:?unable to read pushVersionCode from gradle/libs.versions.toml}"
cat > "$xmsf_dir/xmsf-runtime-manifest.json" <<EOF
{
  "schema": 1,
  "variant": "github",
  "versionName": "$xmsf_version",
  "versionCode": $xmsf_version_code,
  "minManagerVersionCode": $CI_PIPELINE_IID,
  "sha256": "$xmsf_sha"
}
EOF
