#!/usr/bin/env bash
# Publish the XMSF runtime APK to the GitLab generic package registry so the
# github flavor of the manager can download, verify and install it in-process.
#
# Published layout (mirrors XmsfDownloader.registryBaseUrl and its tests):
#   <registryPackage>/<versionName>/xmsf-runtime.apk
#   <registryPackage>/<versionName>/xmsf-runtime.apk.sha256   ("<hash>  xmsf-runtime.apk")
#   <registryPackage>/<versionName>/xmsf-runtime-manifest.json (XmsfRuntimeManifest)
#
# <registryPackage> is one generic package per runtime variant -- xmsf-runtime
# for normal, xmsf-runtime-vc105 for vc105 -- because both variants of a release
# share one versionName and only the versionCode tells them apart. Publishing
# them side by side under one package would let an in-app update swap a device
# between variants, which moves the version code apps compare against.
#
# The runtime must be signed by the same release key as the manager: the
# downloader enforces same-signature against the host app, so publishing an
# APK signed by anything else makes every github install fail at the
# signature gate.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
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
  publish   Reconcile and upload the runtime APK, sidecar and manifest to the
            generic package registry (default). An identical package is reused;
            a conflicting or partial exact package version is replaced safely.
  validate  Only build and locally verify the sidecar and manifest; no upload.

Options:
  --apk PATH                  Runtime APK (default: newest universal release
                              APK of --flavor under xmsf/build/outputs/apk).
  --flavor NAME               Runtime variant: normal (default) or vc105.
                              Selects the generic package the payload is
                              published into (XmsfRuntimeFlavor).
  --version NAME              Runtime versionName (default: from the tag or
                              the APK file name).
  --version-code N            Runtime versionCode (default: aapt2 badging).
  --min-manager-version-code N  Minimum manager versionCode able to use this
                              runtime (default: 0, no floor).
  --variant NAME              Manifest variant, must be "github" (default).
  --commit SHA                Git commit the runtime was built from (default:
                              $CI_COMMIT_SHORT_SHA, else this checkout's HEAD).
                              Diagnostic only; an empty value is published as an
                              empty string.

Environment:
  GITLAB_API_URL     Default CI_API_V4_URL or https://gitlab.com/api/v4
  GITLAB_PROJECT_ID  Default 83955143 (magisk3171/MiPushFramework)
  GITLAB_API_TOKEN   Required masked API token for package operations
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
  local dir="$ROOT_DIR/xmsf/build/outputs/apk/$FLAVOR/release"
  [[ -d "$dir" ]] || return 1
  find "$dir" -type f -name 'universal_*_xmsf_v*_release.apk' | sort | tail -n 1
}

MODE="publish"
FLAVOR="normal"
APK_PATH=""
VERSION_NAME=""
VERSION_CODE=""
MIN_MANAGER_VERSION_CODE="0"
VARIANT="github"
COMMIT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    publish|validate) MODE="$1"; shift ;;
    --apk) APK_PATH="$2"; shift 2 ;;
    --flavor) FLAVOR="$2"; shift 2 ;;
    --version) VERSION_NAME="$2"; shift 2 ;;
    --version-code) VERSION_CODE="$2"; shift 2 ;;
    --min-manager-version-code) MIN_MANAGER_VERSION_CODE="$2"; shift 2 ;;
    --variant) VARIANT="$2"; shift 2 ;;
    --commit) COMMIT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "ERROR: unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR"

case "$FLAVOR" in
  normal) REGISTRY_PACKAGE="xmsf-runtime" ;;
  vc105) REGISTRY_PACKAGE="xmsf-runtime-vc105" ;;
  *)
    echo "ERROR: unknown runtime flavor '$FLAVOR' (expected normal or vc105)" >&2
    exit 2
    ;;
esac

if [[ -z "$APK_PATH" ]]; then
  if ! APK_PATH="$(default_apk)"; then
    echo "ERROR: no runtime APK given and none found under xmsf/build/outputs/apk/$FLAVOR/release" >&2
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
# The flavor tag is gated the same way: a payload must never reach the other
# variant's package, where a host would accept it under its own pin.
apk_base="$(basename "$APK_PATH")"
[[ "$apk_base" == *_"${FLAVOR}"_xmsf_v* ]] || {
  echo "ERROR: APK file name '$apk_base' is not a $FLAVOR runtime build" >&2
  exit 2
}
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

if [[ -z "$COMMIT" ]]; then
  COMMIT="${CI_COMMIT_SHORT_SHA:-$(git rev-parse --short HEAD 2>/dev/null || true)}"
fi

STAGE_DIR=""
make_tmp_dir STAGE_DIR
cp "$APK_PATH" "$STAGE_DIR/xmsf-runtime.apk"

APK_SHA256="$(sha256sum "$STAGE_DIR/xmsf-runtime.apk" | cut -d' ' -f1)"
printf '%s  xmsf-runtime.apk\n' "$APK_SHA256" > "$STAGE_DIR/xmsf-runtime.apk.sha256"

python3 - "$STAGE_DIR/xmsf-runtime-manifest.json" \
  "$VERSION_NAME" "$VERSION_CODE" "$MIN_MANAGER_VERSION_CODE" "$APK_SHA256" "$VARIANT" "$COMMIT" <<'PY'
import json
import sys

output_file, version_name, version_code, min_manager, sha256, variant, commit = sys.argv[1:8]
manifest = {
    "schema": 1,
    "variant": variant,
    "versionName": version_name,
    "versionCode": int(version_code),
    "minManagerVersionCode": int(min_manager),
    "sha256": sha256,
    "commit": commit,
}
with open(output_file, "w", encoding="utf-8") as handle:
    handle.write(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
PY

python3 - "$STAGE_DIR" "$VERSION_NAME" "$VERSION_CODE" "$MIN_MANAGER_VERSION_CODE" "$APK_SHA256" "$VARIANT" <<'LOCAL_PAYLOAD_VALIDATION'
import hashlib
import json
import os
import sys

stage_dir, version_name, version_code, min_manager, expected_sha, variant = sys.argv[1:]
apk_path = os.path.join(stage_dir, "xmsf-runtime.apk")
with open(apk_path, "rb") as handle:
    actual_sha = hashlib.sha256(handle.read()).hexdigest()
if actual_sha != expected_sha:
    raise SystemExit("ERROR: staged APK SHA-256 does not match the locally computed digest")
with open(apk_path + ".sha256", encoding="utf-8") as handle:
    sidecar = handle.read()
if sidecar != f"{actual_sha}  xmsf-runtime.apk\n":
    raise SystemExit("ERROR: staged SHA sidecar does not match the APK")
with open(os.path.join(stage_dir, "xmsf-runtime-manifest.json"), encoding="utf-8") as handle:
    manifest = json.load(handle)
expected = {
    "schema": 1,
    "variant": variant,
    "versionName": version_name,
    "versionCode": int(version_code),
    "minManagerVersionCode": int(min_manager),
    "sha256": actual_sha,
}
if not isinstance(manifest, dict) or any(manifest.get(key) != value for key, value in expected.items()):
    raise SystemExit("ERROR: staged runtime manifest does not match local APK/version inputs")
if not isinstance(manifest.get("commit"), str):
    raise SystemExit("ERROR: staged runtime manifest has an invalid commit field")
print("Validated local APK, SHA sidecar and runtime manifest before registry access")
LOCAL_PAYLOAD_VALIDATION

echo "flavor:             $FLAVOR ($REGISTRY_PACKAGE)"
echo "Runtime APK:        $APK_PATH"
echo "versionName:        $VERSION_NAME"
echo "versionCode:        $VERSION_CODE"
echo "minManagerVersion:  $MIN_MANAGER_VERSION_CODE"
echo "sha256:             $APK_SHA256"
echo "commit:             ${COMMIT:-<none>}"

if [[ "$MODE" == "validate" ]]; then
  echo "Validated runtime payload (no upload): $STAGE_DIR"
  exit 0
fi

GITLAB_API_URL="${GITLAB_API_URL:-${CI_API_V4_URL:-https://gitlab.com/api/v4}}"
GITLAB_API_URL="${GITLAB_API_URL%/}"
GITLAB_PROJECT_ID="${GITLAB_PROJECT_ID:-83955143}"
if [[ -z "${GITLAB_API_TOKEN:-}" ]]; then
  echo "ERROR: GITLAB_API_TOKEN is required for runtime package reconciliation" >&2
  exit 1
fi
PACKAGE_PATH="$REGISTRY_PACKAGE/$VERSION_NAME"
API_WORK_DIR=""
make_tmp_dir API_WORK_DIR
EXACT_PACKAGE_IDS=()

urlencode_query() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

api_request() {
  local method="$1"
  local endpoint="$2"
  local response_file="$3"
  local headers_file="$4"
  local request_status
  shift 4
  local -a retry_args=()
  if [[ "$method" == "GET" ]]; then
    retry_args=(--retry 2 --retry-delay 1)
  fi
  if ! request_status="$(curl --silent --show-error \
    --connect-timeout 20 --max-time 120 \
    "${retry_args[@]}" \
    --header "PRIVATE-TOKEN: $GITLAB_API_TOKEN" \
    --request "$method" \
    --dump-header "$headers_file" \
    --output "$response_file" \
    --write-out '%{http_code}' \
    "$@" \
    "$GITLAB_API_URL/${endpoint#/}")"; then
    echo "ERROR: GitLab API $method request failed: $endpoint" >&2
    return 1
  fi
  if [[ ! "$request_status" =~ ^[0-9]{3}$ ]]; then
    echo "ERROR: GitLab API returned an invalid HTTP status for $method $endpoint" >&2
    return 1
  fi
  printf '%s\n' "$request_status"
}

require_http_status() {
  local status="$1"
  local expected="$2"
  local operation="$3"
  if [[ "$status" != "$expected" ]]; then
    echo "ERROR: $operation returned HTTP $status (expected $expected)" >&2
    return 1
  fi
}

fetch_paged_json() {
  local endpoint_prefix="$1"
  local file_prefix="$2"
  local output_file="$3"
  local page=1
  local total_pages=""
  local next_page current_page page_total status page_header_info
  local body_file headers_file expected_next
  local page_count=0

  printf '[]\n' > "$output_file"
  while :; do
    page_count=$((page_count + 1))
    if [[ "$page_count" -gt 1000 ]]; then
      echo "ERROR: GitLab API pagination exceeded 1000 pages for $endpoint_prefix" >&2
      return 1
    fi
    body_file="$API_WORK_DIR/$file_prefix-page-$page.json"
    headers_file="$API_WORK_DIR/$file_prefix-page-$page.headers"
    status="$(api_request GET "$endpoint_prefix&per_page=100&page=$page" "$body_file" "$headers_file")" || return 1
    require_http_status "$status" 200 "GitLab API GET page $page" || return 1

    if ! page_header_info="$(awk -F: '
      {
        key = tolower($1)
        value = substr($0, index($0, ":") + 1)
        sub(/^[[:space:]]+/, "", value)
        sub(/[\r[:space:]]+$/, "", value)
        if (key == "x-page") { current = value; have_current = 1 }
        if (key == "x-total-pages") { total = value; have_total = 1 }
        if (key == "x-next-page") { next_page_value = value; have_next = 1 }
      }
      END {
        if (!have_current || !have_total || !have_next) exit 1
        printf "%s\t%s\t%s\n", current, total, next_page_value
      }
    ' "$headers_file")"; then
      echo "ERROR: GitLab API pagination headers are incomplete for page $page" >&2
      return 1
    fi
    IFS=$'\t' read -r current_page page_total next_page <<< "$page_header_info"
    if [[ "$current_page" != "$page" || ! "$page_total" =~ ^[0-9]+$ ]]; then
      echo "ERROR: GitLab API pagination metadata is invalid for page $page" >&2
      return 1
    fi
    if [[ -n "$next_page" && ! "$next_page" =~ ^[0-9]+$ ]]; then
      echo "ERROR: GitLab API returned a non-numeric next page for page $page" >&2
      return 1
    fi
    if [[ -n "$total_pages" && "$page_total" != "$total_pages" ]]; then
      echo "ERROR: GitLab API page count changed during pagination for $endpoint_prefix" >&2
      return 1
    fi
    total_pages="$page_total"
    if (( page_total > 0 && page > page_total )); then
      echo "ERROR: GitLab API page $page exceeds reported page count $page_total" >&2
      return 1
    fi
    if [[ -n "$next_page" ]]; then
      expected_next=$((page + 1))
      if [[ "$next_page" != "$expected_next" ]] || (( page_total > 0 && page >= page_total )); then
        echo "ERROR: GitLab API returned an incomplete or invalid pagination sequence at page $page" >&2
        return 1
      fi
    elif (( page_total > page )); then
      echo "ERROR: GitLab API pagination stopped at page $page of $page_total" >&2
      return 1
    fi

    if ! python3 - "$body_file" "$output_file" <<'PY'
import json
import sys

page_path, output_path = sys.argv[1:]
with open(page_path, encoding="utf-8") as handle:
    page = json.load(handle)
if not isinstance(page, list) or any(not isinstance(item, dict) for item in page):
    raise SystemExit("GitLab API page is not an array of objects")
with open(output_path, encoding="utf-8") as handle:
    aggregate = json.load(handle)
if not isinstance(aggregate, list):
    raise SystemExit("GitLab API page accumulator is invalid")
aggregate.extend(page)
with open(output_path, "w", encoding="utf-8") as handle:
    json.dump(aggregate, handle)
PY
    then
      echo "ERROR: GitLab API returned malformed JSON for page $page" >&2
      return 1
    fi

    [[ -n "$next_page" ]] || break
    page="$next_page"
  done
}

package_ids_from_response() {
  local packages_file="$1"
  local output_file="$2"
  python3 - "$packages_file" "$REGISTRY_PACKAGE" "$VERSION_NAME" "$output_file" <<'PY'
import json
import re
import sys

packages_path, expected_name, expected_version, output_path = sys.argv[1:]
with open(packages_path, encoding="utf-8") as handle:
    packages = json.load(handle)
if not isinstance(packages, list):
    raise SystemExit("package list is not an array")
ids = []
for package in packages:
    if not isinstance(package, dict):
        raise SystemExit("package list contains a non-object entry")
    for key in ("name", "version", "package_type", "id"):
        if key not in package:
            raise SystemExit(f"package list entry is missing {key}")
    if package["name"] != expected_name or package["version"] != expected_version:
        continue
    if package["package_type"] != "generic":
        continue
    value = package["id"]
    if isinstance(value, bool) or not re.fullmatch(r"[0-9]+", str(value)) or int(value) < 1:
        raise SystemExit("exact generic package has an invalid id")
    ids.append(str(int(value)))
with open(output_path, "w", encoding="utf-8") as handle:
    handle.write("".join(f"{value}\n" for value in dict.fromkeys(ids)))
PY
}

query_exact_packages() {
  local packages_file="$API_WORK_DIR/packages.json"
  local ids_file="$API_WORK_DIR/package-ids.txt"
  local query
  query="projects/$GITLAB_PROJECT_ID/packages?package_type=generic&package_name=$(urlencode_query "$REGISTRY_PACKAGE")&package_version=$(urlencode_query "$VERSION_NAME")"
  fetch_paged_json "$query" package-list "$packages_file" || return 1
  if ! package_ids_from_response "$packages_file" "$ids_file"; then
    echo "ERROR: GitLab package list did not contain complete package metadata" >&2
    return 1
  fi
  mapfile -t EXACT_PACKAGE_IDS < "$ids_file"
}

fetch_package_files() {
  local package_id="$1"
  local files_file="$API_WORK_DIR/package-$package_id-files.json"
  local endpoint="projects/$GITLAB_PROJECT_ID/packages/$package_id/package_files?"
  local status
  fetch_paged_json "$endpoint" "package-$package_id-files" "$files_file" || return 1
  status=0
  python3 - "$files_file" <<'PY' || status=$?
import json
import re
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    files = json.load(handle)
if not isinstance(files, list):
    raise SystemExit("package files response is not an array")
for item in files:
    if not isinstance(item, dict):
        raise SystemExit("package files response contains a non-object entry")
    for key in ("file_name", "size", "file_sha256"):
        if key not in item:
            raise SystemExit(f"package file metadata is missing {key}")
    if not isinstance(item["file_name"], str) or not item["file_name"]:
        raise SystemExit("package file name is invalid")
    if isinstance(item["size"], bool) or not isinstance(item["size"], int) or item["size"] < 0:
        raise SystemExit("package file size is invalid")
    if not isinstance(item["file_sha256"], str) or not re.fullmatch(r"[0-9a-fA-F]{64}", item["file_sha256"]):
        raise SystemExit("package file SHA-256 is missing or invalid")
PY
  if [[ "$status" -ne 0 ]]; then
    echo "ERROR: GitLab package-files response was incomplete for package $package_id" >&2
    return 2
  fi
  printf '%s\n' "$files_file"
}

package_files_match_local() {
  local files_file="$1"
  local expected_file="$API_WORK_DIR/expected-files.json"
  local status=0
  python3 - "$files_file" "$expected_file" <<'PY' || status=$?
import json
import sys

files_path, expected_path = sys.argv[1:]
with open(files_path, encoding="utf-8") as handle:
    files = json.load(handle)
with open(expected_path, encoding="utf-8") as handle:
    expected = json.load(handle)
by_name = {}
for item in files:
    name = item["file_name"]
    if name in by_name:
        raise SystemExit(1)
    by_name[name] = item
if set(by_name) != set(expected) or len(files) != len(expected):
    raise SystemExit(1)
for name, local in expected.items():
    remote = by_name[name]
    if remote["size"] != local["size"] or remote["file_sha256"].lower() != local["sha256"]:
        raise SystemExit(1)
PY
  case "$status" in
    0) return 0 ;;
    1) return 1 ;;
    *)
      echo "ERROR: failed to compare GitLab package file metadata" >&2
      return 2
      ;;
  esac
}

make_expected_file_metadata() {
  local output_file="$API_WORK_DIR/expected-files.json"
  python3 - "$STAGE_DIR" "$output_file" <<'PY'
import hashlib
import json
import os
import sys

stage_dir, output_path = sys.argv[1:]
expected = {}
for name in (
    "xmsf-runtime.apk",
    "xmsf-runtime.apk.sha256",
    "xmsf-runtime-manifest.json",
):
    path = os.path.join(stage_dir, name)
    with open(path, "rb") as handle:
        content = handle.read()
    expected[name] = {"size": len(content), "sha256": hashlib.sha256(content).hexdigest()}
with open(output_path, "w", encoding="utf-8") as handle:
    json.dump(expected, handle, sort_keys=True)
PY
}

verify_exact_package_absent() {
  query_exact_packages || return 1
  if [[ "${#EXACT_PACKAGE_IDS[@]}" -ne 0 ]]; then
    echo "ERROR: exact package version still exists after deletion; refusing upload" >&2
    return 1
  fi
}

delete_exact_packages() {
  local package_id status response_file headers_file
  local -a package_ids=("${EXACT_PACKAGE_IDS[@]}")
  for package_id in "${package_ids[@]}"; do
    response_file="$API_WORK_DIR/delete-$package_id.json"
    headers_file="$API_WORK_DIR/delete-$package_id.headers"
    status="$(api_request DELETE "projects/$GITLAB_PROJECT_ID/packages/$package_id" "$response_file" "$headers_file")" || return 1
    case "$status" in
      200|202|204) printf 'Deleted exact package version %s/%s (package id %s)\n' "$REGISTRY_PACKAGE" "$VERSION_NAME" "$package_id" ;;
      404) printf 'Package id %s was already absent; verifying the exact version before upload\n' "$package_id" ;;
      *)
        echo "ERROR: deleting exact package id $package_id returned HTTP $status; refusing upload" >&2
        return 1
        ;;
    esac
  done
  verify_exact_package_absent
}

upload_payload_file() {
  local local_file="$1"
  local remote_name="$2"
  local package_endpoint status response_file headers_file
  package_endpoint="projects/$GITLAB_PROJECT_ID/packages/generic/$(urlencode "$PACKAGE_PATH/$remote_name")"
  response_file="$API_WORK_DIR/upload-$remote_name.response"
  headers_file="$API_WORK_DIR/upload-$remote_name.headers"
  status="$(api_request PUT "$package_endpoint" "$response_file" "$headers_file" --upload-file "$local_file")" || return 1
  case "$status" in
    200|201|202)
      printf 'Published: %s/%s\n' "$PACKAGE_PATH" "$remote_name"
      ;;
    *)
      echo "ERROR: uploading $PACKAGE_PATH/$remote_name returned HTTP $status" >&2
      return 1
      ;;
  esac
}

verify_uploaded_package() {
  local package_id files_file
  query_exact_packages || return 1
  if [[ "${#EXACT_PACKAGE_IDS[@]}" -ne 1 ]]; then
    echo "ERROR: upload verification found ${#EXACT_PACKAGE_IDS[@]} exact package records (expected one)" >&2
    return 1
  fi
  package_id="${EXACT_PACKAGE_IDS[0]}"
  files_file="$(fetch_package_files "$package_id")" || return 1
  if ! package_files_match_local "$files_file"; then
    echo "ERROR: uploaded package does not contain the exact local three-file payload" >&2
    return 1
  fi
  printf 'Verified exact package version %s/%s: all three assets match local SHA-256 and size\n' "$REGISTRY_PACKAGE" "$VERSION_NAME"
}

make_expected_file_metadata
query_exact_packages
if [[ "${#EXACT_PACKAGE_IDS[@]}" -eq 0 ]]; then
  echo "No exact generic package version exists; uploading a complete payload"
else
  local_match_count=0
  if [[ "${#EXACT_PACKAGE_IDS[@]}" -eq 1 ]]; then
    package_files="$(fetch_package_files "${EXACT_PACKAGE_IDS[0]}")" || exit $?
    match_status=0
    package_files_match_local "$package_files" || match_status=$?
    case "$match_status" in
      0) local_match_count=1 ;;
      1) ;;
      *) exit "$match_status" ;;
    esac
  fi
  if [[ "$local_match_count" -eq 1 ]]; then
    printf 'Reusing identical exact package version %s/%s\n' "$REGISTRY_PACKAGE" "$VERSION_NAME"
    verify_uploaded_package
    exit 0
  fi
  echo "Exact package version is conflicting or incomplete; deleting only its exact package records"
  delete_exact_packages
fi

# The manifest is the commit marker for the payload and is always uploaded last.
upload_payload_file "$STAGE_DIR/xmsf-runtime.apk" "xmsf-runtime.apk"
upload_payload_file "$STAGE_DIR/xmsf-runtime.apk.sha256" "xmsf-runtime.apk.sha256"
upload_payload_file "$STAGE_DIR/xmsf-runtime-manifest.json" "xmsf-runtime-manifest.json"
verify_uploaded_package

printf 'Done. Manager github flavor will fetch %s from %s/projects/%s\n' "$PACKAGE_PATH" "$GITLAB_API_URL" "$GITLAB_PROJECT_ID"
