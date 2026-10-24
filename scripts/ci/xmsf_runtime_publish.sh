#!/usr/bin/env bash
# Publish the XMSF runtime APK to the GitLab generic package registry so the
# github flavor of the manager can download, verify and install it in-process.
#
# Published layout (mirrors XmsfDownloader.BASE_URL and its tests):
#   xmsf-runtime/<versionName>/xmsf-runtime.apk
#   xmsf-runtime/<versionName>/xmsf-runtime.apk.sha256   ("<hash>  xmsf-runtime.apk")
#   xmsf-runtime/<versionName>/xmsf-runtime-manifest.json (XmsfRuntimeManifest)
#
# The runtime must be signed by the same release key as the manager: the
# downloader enforces same-signature against the host app, so publishing an
# APK signed by anything else makes every github install fail at the
# signature gate.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TOOLKIT_DIR="${MAGISK_CI_TOOLKIT_DIR:-$(bash "$ROOT_DIR/scripts/resolve_ci_toolkit.sh")}"
source "$TOOLKIT_DIR/ci/gitlab_api.sh"
TMP_FILES=()
TMP_DIRS=()

cleanup_tmp_files() {
  local file dir
  for file in "${TMP_FILES[@]:-}"; do
    [[ -n "$file" ]] && rm -f "$file"
  done
  for dir in "${TMP_DIRS[@]:-}"; do
    [[ -n "$dir" ]] && rm -rf "$dir"
  done
}
trap cleanup_tmp_files EXIT

make_tmp_file() {
  local output_var="$1"
  local file
  file="$(mktemp)"
  TMP_FILES+=("$file")
  printf -v "$output_var" '%s' "$file"
}

make_tmp_dir() {
  local output_var="$1"
  local dir
  dir="$(mktemp -d)"
  TMP_DIRS+=("$dir")
  printf -v "$output_var" '%s' "$dir"
}

usage() {
  cat >&2 <<'EOF'
Usage:
  xmsf_runtime_publish.sh [options] [publish|validate]

Modes:
  publish   Upload the runtime APK, sidecar and manifest to the generic
            package registry (default). Idempotent: an identical published
            asset is reused, a conflicting one fails the run.
  validate  Only build and locally verify the sidecar and manifest; no upload.

Options:
  --apk PATH                  Runtime APK (default: newest universal normal
                              release APK under xmsf/build/outputs/apk).
  --version NAME              Runtime versionName (default: from the tag or
                              the APK file name).
  --version-code N            Runtime versionCode (default: aapt2 badging).
  --min-manager-version-code N  Minimum manager versionCode able to use this
                              runtime (default: 0, no floor).
  --variant NAME              Manifest variant, must be "github" (default).

Environment:
  GITLAB_API_URL     Default https://gitlab.com/api/v4
  GITLAB_PROJECT_ID  Default 83955143 (magisk3171/MiPushFramework)
  GITLAB_TOKEN       Private token; falls back to CI_JOB_TOKEN.
EOF
}

urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe="/"))' "$1"
}

require_supported_env() {
  local name
  local missing=()
  for name in "$@"; do
    if [[ -z "${!name:-}" ]]; then
      missing+=("$name")
    fi
  done
  if [[ "${#missing[@]}" -gt 0 ]]; then
    echo "ERROR: missing ${missing[*]}" >&2
    return 1
  fi
}

gitlab_curl() { gitlab_api_curl_url "$@"; }

find_aapt2() {
  local candidate
  for candidate in "${ANDROID_HOME:-/opt/android-sdk}"/build-tools/*/aapt2; do
    # Probe-run before trusting the candidate: the release stage runs in the
    # musl-based utility image, where a mounted glibc aapt2 passes the -x
    # test but cannot execute at all (missing ld-linux interpreter).
    if [[ -x "$candidate" ]] && "$candidate" --version >/dev/null 2>&1; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

release_tag() {
  if [[ -n "${CI_COMMIT_TAG:-}" ]]; then
    printf '%s\n' "$CI_COMMIT_TAG"
  elif [[ -n "${GITHUB_REF_NAME:-}" ]]; then
    printf '%s\n' "${GITHUB_REF_NAME#v}"
  fi
}

default_apk() {
  # Flavoured AGP layout: apk/<flavor>/<buildType>/<split>_<flavor>_xmsf_v<version>_<buildType>.apk
  local dir="$ROOT_DIR/xmsf/build/outputs/apk/normal/release"
  [[ -d "$dir" ]] || return 1
  find "$dir" -type f -name 'universal_*_xmsf_v*_release.apk' | sort | tail -n 1
}

MODE="publish"
APK_PATH=""
VERSION_NAME=""
VERSION_CODE=""
MIN_MANAGER_VERSION_CODE="0"
VARIANT="github"

while [[ $# -gt 0 ]]; do
  case "$1" in
    publish|validate) MODE="$1"; shift ;;
    --apk) APK_PATH="$2"; shift 2 ;;
    --version) VERSION_NAME="$2"; shift 2 ;;
    --version-code) VERSION_CODE="$2"; shift 2 ;;
    --min-manager-version-code) MIN_MANAGER_VERSION_CODE="$2"; shift 2 ;;
    --variant) VARIANT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "ERROR: unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR"

if [[ -z "$APK_PATH" ]]; then
  if ! APK_PATH="$(default_apk)"; then
    echo "ERROR: no runtime APK given and none found under xmsf/build/outputs/apk/normal/release" >&2
    exit 2
  fi
fi
[[ -f "$APK_PATH" ]] || {
  echo "ERROR: runtime APK not found: $APK_PATH" >&2
  exit 2
}

if [[ -z "$VERSION_NAME" ]]; then
  tag_name="$(release_tag || true)"
  if [[ -n "$tag_name" ]]; then
    VERSION_NAME="${tag_name#v}"
  else
    VERSION_NAME="$(basename "$APK_PATH" | sed -nE 's/.*_xmsf_v([0-9][^_]*)_release\.apk/\1/p')"
  fi
fi
[[ -n "$VERSION_NAME" ]] || {
  echo "ERROR: unable to determine the runtime version name; pass --version" >&2
  exit 2
}

# The APK file name encodes the versionName the manifest carries (AGP names
# split outputs from versionName), so the name gates the version even where
# aapt2 is unavailable -- the release stage runs on the utility image, which
# ships no Android SDK. This mirrors publish_mipush_release.sh, which verifies
# release assets by "<abi>_<flavor>_xmsf_v<version>_release.apk" basenames.
apk_base="$(basename "$APK_PATH")"
[[ "$apk_base" == *_xmsf_v"${VERSION_NAME}"_release.apk ]] || {
  echo "ERROR: APK file name '$apk_base' does not encode version '$VERSION_NAME' (expected *_xmsf_v${VERSION_NAME}_release.apk)" >&2
  exit 2
}

AAPT2="$(find_aapt2 || true)"
if [[ -z "$AAPT2" ]]; then
  if [[ -z "$VERSION_CODE" ]]; then
    echo "ERROR: aapt2 not found under \${ANDROID_HOME:-/opt/android-sdk}/build-tools and no --version-code given" >&2
    exit 2
  fi
  echo "WARN: aapt2 not found; skipping badging cross-check (version inputs taken from release env)"
else
  if badging="$("$AAPT2" dump badging "$APK_PATH" 2>/dev/null)" && [[ -n "$badging" ]]; then
    if [[ -z "$VERSION_CODE" ]]; then
      VERSION_CODE="$(printf '%s\n' "$badging" | sed -nE "s/^package:.*versionCode='([0-9]+)'.*/\1/p" | head -n 1)"
    fi
    badging_version="$(printf '%s\n' "$badging" | sed -nE "s/^package:.*versionName='([^']+)'.*/\1/p" | head -n 1)"
    [[ "$badging_version" == "$VERSION_NAME" ]] || {
      echo "ERROR: APK versionName '$badging_version' does not match requested '$VERSION_NAME'" >&2
      exit 2
    }
  else
    echo "WARN: aapt2 badging unavailable (probe passed but dump failed/empty); skipping cross-check (the APK file-name gate still binds the version)" >&2
  fi
fi
[[ -n "$VERSION_CODE" ]] || {
  echo "ERROR: unable to read the runtime versionCode; pass --version-code" >&2
  exit 2
}
[[ "$VARIANT" == "github" ]] || {
  echo "ERROR: manifest variant must be 'github' (the only flavor that downloads in-process), got '$VARIANT'" >&2
  exit 2
}

STAGE_DIR=""
make_tmp_dir STAGE_DIR
cp "$APK_PATH" "$STAGE_DIR/xmsf-runtime.apk"

APK_SHA256="$(sha256sum "$STAGE_DIR/xmsf-runtime.apk" | cut -d' ' -f1)"
printf '%s  xmsf-runtime.apk\n' "$APK_SHA256" > "$STAGE_DIR/xmsf-runtime.apk.sha256"

python3 - "$STAGE_DIR/xmsf-runtime-manifest.json" \
  "$VERSION_NAME" "$VERSION_CODE" "$MIN_MANAGER_VERSION_CODE" "$APK_SHA256" "$VARIANT" <<'PY'
import json
import sys

output_file, version_name, version_code, min_manager, sha256, variant = sys.argv[1:7]
manifest = {
    "schema": 1,
    "variant": variant,
    "versionName": version_name,
    "versionCode": int(version_code),
    "minManagerVersionCode": int(min_manager),
    "sha256": sha256,
}
with open(output_file, "w", encoding="utf-8") as handle:
    handle.write(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
PY

echo "Runtime APK:        $APK_PATH"
echo "versionName:        $VERSION_NAME"
echo "versionCode:        $VERSION_CODE"
echo "minManagerVersion:  $MIN_MANAGER_VERSION_CODE"
echo "sha256:             $APK_SHA256"

if [[ "$MODE" == "validate" ]]; then
  echo "Validated runtime payload (no upload): $STAGE_DIR"
  exit 0
fi

GITLAB_API_URL="${GITLAB_API_URL:-https://gitlab.com/api/v4}"
GITLAB_PROJECT_ID="${GITLAB_PROJECT_ID:-83955143}"
PACKAGE_PATH="xmsf-runtime/$VERSION_NAME"

publish_payload_file() {
  local local_file="$1"
  local remote_name="$2"
  # Assigned indirectly by make_tmp_file (printf -v); declared here so the
  # variable is always defined and shellcheck can see the assignment target.
  local remote_copy=""
  local package_url
  package_url="$GITLAB_API_URL/projects/$GITLAB_PROJECT_ID/packages/generic/$(urlencode "$PACKAGE_PATH/$remote_name")"

  if gitlab_curl --fail --output /dev/null "$package_url"; then
    make_tmp_file remote_copy
    if gitlab_curl --fail --output "$remote_copy" "$package_url" && cmp --silent "$local_file" "$remote_copy"; then
      echo "Reusing identical package asset: $PACKAGE_PATH/$remote_name"
      return 0
    fi
    echo "ERROR: package asset already exists with different content: $PACKAGE_PATH/$remote_name" >&2
    return 1
  fi

  gitlab_curl --fail --upload-file "$local_file" "$package_url" > /dev/null
  echo "Published: $PACKAGE_PATH/$remote_name"
}

publish_payload_file "$STAGE_DIR/xmsf-runtime.apk" "xmsf-runtime.apk"
publish_payload_file "$STAGE_DIR/xmsf-runtime.apk.sha256" "xmsf-runtime.apk.sha256"
publish_payload_file "$STAGE_DIR/xmsf-runtime-manifest.json" "xmsf-runtime-manifest.json"

echo "Done. Manager github flavor will fetch $PACKAGE_PATH from $GITLAB_API_URL/projects/$GITLAB_PROJECT_ID"
