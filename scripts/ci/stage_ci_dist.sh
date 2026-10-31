#!/usr/bin/env bash
# Stages the ci-dist artifact layout consumed by the in-app update checks.
#
#   ci-dist/<channelRoot>/version.txt                    plain-text version sidecar
#   ci-dist/<channelRoot>/<versionName>/<apk>            version-keyed payload
#   ci-dist/<channelRoot>/<versionName>/<apk>.sha256     checksum sidecar
#   ci-dist/<channelRoot>/<versionName>/<manifest>       UpdateManifest JSON
#
# The manager has one channel root (its package name); the XMSF runtime has one
# per build variant, because both variants of a build share one versionName and
# differ only in versionCode -- a device must never be handed the other variant
# by an update (see XmsfRuntimeFlavor).
#
# All channels share one layout. The manager's OTA probe
# (GitLabCiArtifactUpdateSource) and the XMSF runtime probe
# (XmsfCiArtifactProbe) resolve the <versionName> segment from their sidecar
# and verify the sha256 before anything installs, so a staged set must always
# travel complete: APK + .sha256 + manifest.
#
# Version gates ride the allocated versionCode — the same number the APK itself
# carries (the build template allocates it and exports MAGISK_VERSION_CODE) — so
# old managers never pull a build they cannot run. It is deliberately NOT the
# pipeline iid any more: the in-app updater judges a build by this manifest's
# versionCode, and the iid is no longer that number.
#
# GitLab runs after_script in its own shell, so the export made in script does
# not reach here. The toolkit writes the number to .magisk-version-code instead,
# and ci/allocated_version_code.sh reads it back (preferring MAGISK_VERSION_CODE
# when it is set) and fails loudly naming both sources when neither is there —
# the alternative being a green build whose manifest silently disappears and an
# updater that falls back to the pipeline iid.
#
# Called from android:build's after_script; kept out of the YAML so the
# heredocs below stay valid shell (YAML block scalars cannot host a
# top-level heredoc terminator).
set -euo pipefail

cd "$CI_PROJECT_DIR"

version_code="${MAGISK_VERSION_CODE:-}"
if [ -z "$version_code" ] && [ -n "${MAGISK_CI_TOOLKIT_DIR:-}" ] \
   && [ -x "$MAGISK_CI_TOOLKIT_DIR/ci/allocated_version_code.sh" ]; then
  version_code="$(bash "$MAGISK_CI_TOOLKIT_DIR/ci/allocated_version_code.sh")"
fi
: "${version_code:?allocated versionCode unavailable: MAGISK_VERSION_CODE is unset and .magisk-version-code is missing or empty}"

# ---------------------------------------------------------------------------
# Manager APK: io.github.magisk317.mipush/<versionName>/mipush-ci.apk
# ---------------------------------------------------------------------------
mipush_apk=$(find mipush/build/outputs/apk/github/debug -type f -name 'universal_*.apk' | sort | tail -n 1)
mipush_version=$(basename "$mipush_apk" | sed -n 's/^universal_.*_v\(.*\)_debug\.apk$/\1/p')
# The channel directory is named after this value, and a name only survives the
# client if it carries the build timestamp: that timestamp is what keeps two
# pipelines of one versionName in two directories instead of overwriting each
# other's APK. ui-kit applies the same shape when it reads version.txt
# (AppUpdateChecker.kt, ciSidecarVersionShape), so anything accepted here would
# otherwise stage a path no client resolves -- a green job whose update channel
# reports "up to date" forever. Fail the build instead.
if ! printf '%s' "$mipush_version" | grep -Eq '^[A-Za-z0-9._+-]+-[0-9]{8}_[0-9]{6}$'; then
  echo "cannot resolve versionName from the manager APK name: $mipush_apk (got: ${mipush_version:-none})" >&2
  exit 1
fi
mipush_dir="ci-dist/io.github.magisk317.mipush/$mipush_version"
mkdir -p "$mipush_dir"
cp "$mipush_apk" "$mipush_dir/mipush-ci.apk"
mipush_sha=$(sha256sum "$mipush_dir/mipush-ci.apk" | cut -d' ' -f1)
printf '%s  mipush-ci.apk\n' "$mipush_sha" > "$mipush_dir/mipush-ci.apk.sha256"
printf '%s' "$mipush_version" > ci-dist/io.github.magisk317.mipush/version.txt
# Provenance for the in-app prompt: ui-kit's UpdateManifest carries branch and
# commit as their own fields and the dialog renders them as a localized note, so
# the manager manifest declares them the way the runtime manifest declares its
# commit. Unresolvable values stage as empty rather than failing the job: this
# is diagnostic text, not a download gate.
manager_branch="${CI_COMMIT_REF_NAME:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)}"
manager_commit="${CI_COMMIT_SHORT_SHA:-$(git rev-parse --short=8 HEAD 2>/dev/null || true)}"
cat > "$mipush_dir/mipush-ci-manifest.json" <<EOF
{
  "schema": 1,
  "variant": "github",
  "versionName": "$mipush_version",
  "versionCode": $version_code,
  "sha256": "$mipush_sha",
  "force": true,
  "minSupportedVersionCode": $version_code,
  "branch": "$manager_branch",
  "commit": "$manager_commit"
}
EOF

# ---------------------------------------------------------------------------
# XMSF runtime APK: one channel root per build variant
#
#   ci-dist/com.xiaomi.xmsf/<versionName>/          normal variant
#   ci-dist/com.xiaomi.xmsf-vc105/<versionName>/    vc105 variant
#
# Each runtime manifest also declares the commit it was built from, so a user can
# match a runtime to a manager without unpacking either APK: the version name
# carries a build timestamp (two builds of one commit differ), and the checksum
# only says the bytes survived the download.
#
# Keyed by the runtime's own package name plus the variant, which is the same
# two-level shape the generic package registry uses, so the in-app downloader
# only swaps its base URL and keeps its whole verification chain (manifest gate
# -> sha256 sidecar -> same-signature check). The manifest's versionCode is the
# variant's own code, which is what lets a host reject a payload staged into the
# wrong root.
#
# The runtime builds under its own "version" flavor dimension (normal/vc105) and
# CI assembles every flavor, so each variant is read from its own output
# directory instead of guessing. after_script failures cannot fail the job, so a
# missing normal APK exits loudly (it is the channel the release pipeline
# publishes) while a missing vc105 APK only skips its own root.
# ---------------------------------------------------------------------------
stage_runtime_variant() {
  local flavor="$1"
  local channel_root="$2"
  local version_code_key="$3"
  local required="$4"
  local apk
  local version
  local runtime_version_code
  local stage_dir
  local sha

  # || true: a missing flavor directory makes find exit non-zero, and under
  # pipefail that status would abort the whole stager instead of just skipping
  # this channel.
  apk=$(find "xmsf/build/outputs/apk/${flavor}/debug" -type f \
    -name 'universal_*_xmsf_v*_debug.apk' 2>/dev/null | sort | tail -n 1 || true)
  if [ -z "$apk" ]; then
    if [ "$required" = "required" ]; then
      echo "ERROR: no universal xmsf debug APK under xmsf/build/outputs/apk/${flavor}/debug" >&2
      exit 1
    fi
    echo "WARN: no ${flavor} runtime APK; skipping its ci-dist channel" >&2
    return 0
  fi

  version=$(basename "$apk" | sed -n 's/^universal_.*_xmsf_v\(.*\)_debug\.apk$/\1/p')
  # Same gate as the manager path above, for the same reason: this value names the
  # runtime channel directory. It applies to the optional variants too -- a name
  # that cannot be resolved is not the same fault as an APK that was never built,
  # and quietly skipping a whole channel is exactly the silent failure this gate
  # exists to prevent.
  if ! printf '%s' "$version" | grep -Eq '^[A-Za-z0-9._+-]+-[0-9]{8}_[0-9]{6}$'; then
    echo "cannot resolve versionName from the runtime APK name: $apk (got: ${version:-none})" >&2
    exit 1
  fi
  # The runtime versionCode comes from the version catalog, not from the
  # manager's allocated code: the two live on different scales. An unreadable
  # readout would stage invalid JSON both probes choke on, so fail loudly
  # naming the source instead.
  runtime_version_code="$(sed -nE "s/^${version_code_key} = \"([0-9]+)\"\$/\1/p" gradle/libs.versions.toml)"
  : "${runtime_version_code:?unable to read ${version_code_key} from gradle/libs.versions.toml}"

  # Diagnostic only, so an unknown commit is staged as an empty string rather
  # than failing the job: CI exports the short sha, a local run falls back to
  # the checkout, and a workspace with neither simply declares nothing.
  local runtime_commit
  runtime_commit="${CI_COMMIT_SHORT_SHA:-$(git rev-parse --short=8 HEAD 2>/dev/null || true)}"

  stage_dir="ci-dist/${channel_root}/${version}"
  mkdir -p "$stage_dir"
  cp "$apk" "$stage_dir/xmsf-runtime.apk"
  sha=$(sha256sum "$stage_dir/xmsf-runtime.apk" | cut -d' ' -f1)
  printf '%s  xmsf-runtime.apk\n' "$sha" > "$stage_dir/xmsf-runtime.apk.sha256"
  printf '%s' "$version" > "ci-dist/${channel_root}/version.txt"
  cat > "$stage_dir/xmsf-runtime-manifest.json" <<EOF
{
  "schema": 1,
  "variant": "github",
  "versionName": "$version",
  "versionCode": $runtime_version_code,
  "minManagerVersionCode": $version_code,
  "sha256": "$sha",
  "commit": "$runtime_commit"
}
EOF
  echo "Staged ${flavor} runtime $version ($runtime_version_code) at $stage_dir"
}

# Channel roots and version-code keys must match XmsfRuntimeFlavor.
stage_runtime_variant normal com.xiaomi.xmsf pushVersionCode required
stage_runtime_variant vc105 com.xiaomi.xmsf-vc105 vc105VersionCode optional
