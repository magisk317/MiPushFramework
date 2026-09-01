#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_FILES=()

cleanup_tmp_files() {
  local file
  for file in "${TMP_FILES[@]}"; do
    rm -f "$file"
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

usage() {
  cat >&2 <<'EOF'
Usage:
  publish_mipush_release.sh release-notes
  publish_mipush_release.sh validate-assets
  publish_mipush_release.sh gitlab-release

Modes:
  release-notes   Extract the current tag section from docs/CHANGELOG.md.
  validate-assets Validate the required APK/optional Zygisk asset set.
  gitlab-release  Publish XMSF APK, MiPush APK, Zygisk zip, and debug files to GitLab Release.
EOF
}

urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
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
    echo "SKIP: unsupported environment; missing ${missing[*]}"
    return 1
  fi
}

gitlab_curl() {
  if [[ -n "${GITLAB_RELEASE_TOKEN:-}" ]]; then
    curl --silent --show-error --location --header "PRIVATE-TOKEN: ${GITLAB_RELEASE_TOKEN}" "$@"
  else
    curl --silent --show-error --location --header "JOB-TOKEN: ${CI_JOB_TOKEN}" "$@"
  fi
}

release_tag() {
  if [[ -n "${CI_COMMIT_TAG:-}" ]]; then
    printf '%s\n' "$CI_COMMIT_TAG"
  elif [[ -n "${GITHUB_REF_NAME:-}" ]]; then
    printf '%s\n' "$GITHUB_REF_NAME"
  else
    echo "ERROR: release tag is required" >&2
    exit 2
  fi
}

generate_release_notes() {
  local tag_name="$1"
  local output_file="$2"
  local platform="${3:-source}"
  local changelog_file="${MAGISK_RELEASE_CHANGELOG_FILE:-docs/CHANGELOG.md}"
  local changelog_extract compare_range previous_tag compare_to compare_url
  make_tmp_file changelog_extract

  if [[ -f "$changelog_file" ]] && awk -v version="$tag_name" '
    $0 == "---" && found { exit }
    found { print; next }
    index($0, "## [" version "]") == 1 { found = 1; print; next }
    END { if (!found) exit 1 }
  ' "$changelog_file" > "$changelog_extract"; then
    :
  else
    printf 'MiPushFramework Release %s\n' "$tag_name" > "$changelog_extract"
  fi

  compare_range="$(sed -nE 's#.*compare/([^[:space:]]+)\.\.\.([^[:space:]]+).*#\1 \2#p' "$changelog_extract" | head -n 1)"
  previous_tag=""
  compare_to=""
  if [[ -n "$compare_range" ]]; then
    read -r previous_tag compare_to <<< "$compare_range"
  fi
  if [[ -n "$previous_tag" && "$compare_to" == "$tag_name" ]]; then
    compare_url=""
    case "$platform" in
      github)
        compare_url="https://github.com/${GITHUB_REPOSITORY:-magisk317/MiPushFramework}/compare/${previous_tag}...${tag_name}"
        ;;
      gitlab)
        compare_url="${CI_PROJECT_URL:-https://gitlab.com/magisk3171/MiPushFramework}/-/compare/${previous_tag}...${tag_name}"
        ;;
      source) ;;
      *)
        echo "ERROR: unsupported release-notes platform: $platform" >&2
        return 2
        ;;
    esac
    if [[ -n "$compare_url" ]]; then
      awk -v replacement="> Full Changelog: ${compare_url}" '
        /^[[:space:]]*>[[:space:]]*Full Changelog:/ { print replacement; next }
        { print }
      ' "$changelog_extract" > "$output_file"
    else
      cat "$changelog_extract" > "$output_file"
    fi
  else
    cat "$changelog_extract" > "$output_file"
  fi

  [[ -s "$output_file" ]] || {
    echo "ERROR: generated release notes are empty: $output_file" >&2
    return 1
  }
}

find_if_dir() {
  local dir="$1"
  shift
  if [[ -d "$dir" ]]; then
    find "$dir" "$@"
  fi
}

require_asset_basename() {
  local expected_name="$1"
  shift
  local path matches=0
  for path in "$@"; do
    if [[ "$(basename "$path")" == "$expected_name" ]]; then
      matches=$((matches + 1))
    fi
  done
  if [[ "$matches" -ne 1 ]]; then
    echo "ERROR: expected exactly one release asset named $expected_name, found $matches" >&2
    return 1
  fi
}

collect_and_validate_release_assets() {
  local tag_name="$1"
  local version_name="${tag_name#v}"
  local abi flavor expected_name
  local zygisk_asset_dir="${MIPUSH_ZYGISK_ASSET_DIR:-MiPushZygisk/build}"
  local zygisk_skip_marker="$zygisk_asset_dir/.zygisk-skip"
  local -a expected_abis=(arm64-v8a armeabi-v7a universal x86 x86_64)

  mapfile -t xmsf_release_assets < <(
    find_if_dir xmsf/build/outputs/apk -type f -path '*/release/*.apk' | sort
  )
  mapfile -t mipush_release_assets < <(
    find_if_dir mipush/build/outputs/apk -type f -path '*/release/*.apk' | sort
  )
  mapfile -t zygisk_release_assets < <(
    find_if_dir "$zygisk_asset_dir" -maxdepth 1 -type f -name '*.zip' | sort
  )
  mapfile -t release_support_assets < <(
    {
      find_if_dir xmsf/build/outputs/mapping -type f -name mapping.txt
      find_if_dir xmsf/build/outputs/native-debug-symbols -type f -name native-debug-symbols.zip
    } | sort
  )

  if [[ "${#xmsf_release_assets[@]}" -ne 10 ]]; then
    echo "ERROR: expected 10 XMSF release APKs (normal/vc105 x 5 ABIs), found ${#xmsf_release_assets[@]}" >&2
    return 1
  fi
  for flavor in normal vc105; do
    for abi in "${expected_abis[@]}"; do
      expected_name="${abi}_${flavor}_xmsf_v${version_name}_release.apk"
      require_asset_basename "$expected_name" "${xmsf_release_assets[@]}" || return 1
    done
  done

  if [[ "${#mipush_release_assets[@]}" -ne 5 ]]; then
    echo "ERROR: expected 5 MiPush release APKs, found ${#mipush_release_assets[@]}" >&2
    return 1
  fi
  for abi in "${expected_abis[@]}"; do
    expected_name="${abi}_MiPush_v${version_name}_release.apk"
    require_asset_basename "$expected_name" "${mipush_release_assets[@]}" || return 1
  done

  if [[ "${#zygisk_release_assets[@]}" -eq 0 ]]; then
    if [[ -f "$zygisk_skip_marker" ]] && grep -Fxq "source-unavailable" "$zygisk_skip_marker"; then
      echo "SKIP: Zygisk source was unavailable; publishing required APKs only."
    else
      echo "ERROR: no Zygisk release assets or supported skip marker were produced" >&2
      return 1
    fi
  else
    if [[ -e "$zygisk_skip_marker" ]]; then
      echo "ERROR: Zygisk release assets and a skip marker were produced together" >&2
      return 1
    fi
    if [[ "${#zygisk_release_assets[@]}" -ne 5 ]]; then
      echo "ERROR: a partial Zygisk release asset set was produced; expected 5 ZIPs, found ${#zygisk_release_assets[@]}" >&2
      return 1
    fi
    for abi in "${expected_abis[@]}"; do
      expected_name="${abi}_MiPushZygisk_v${version_name}_release.zip"
      require_asset_basename "$expected_name" "${zygisk_release_assets[@]}" || return 1
    done
  fi

  release_assets=(
    "${xmsf_release_assets[@]}"
    "${mipush_release_assets[@]}"
    "${zygisk_release_assets[@]}"
    "${release_support_assets[@]}"
  )
}

copy_assets() {
  local output_dir="$1"
  shift
  rm -rf "$output_dir"
  mkdir -p "$output_dir"

  declare -A used_names=()
  local source_file base_name asset_name prefix
  for source_file in "$@"; do
    base_name="$(basename "$source_file")"
    asset_name="$base_name"
    if [[ -n "${used_names[$asset_name]:-}" ]]; then
      prefix="$(dirname "$source_file" | tr '/[:space:]' '__')"
      asset_name="${prefix}_${base_name}"
    fi
    used_names[$asset_name]=1
    cp "$source_file" "$output_dir/$asset_name"
  done
}

verify_existing_package_asset() {
  local local_asset="$1"
  local package_url="$2"
  local remote_asset="${3:-}"
  if [[ -z "$remote_asset" ]]; then
    make_tmp_file remote_asset
    if ! gitlab_curl --fail --output "$remote_asset" "$package_url"; then
      echo "ERROR: package asset already exists but could not be downloaded for comparison: $(basename "$local_asset")" >&2
      return 1
    fi
  fi
  if ! cmp --silent "$local_asset" "$remote_asset"; then
    echo "ERROR: package asset already exists with different content: $(basename "$local_asset")" >&2
    return 1
  fi
  echo "Reusing identical package asset: $(basename "$local_asset")"
}

verify_existing_release_link() {
  local links_url="$1"
  local expected_name="$2"
  local expected_url="$3"
  local expected_path="$4"
  local expected_type="$5"
  local existing_links
  make_tmp_file existing_links
  if ! gitlab_curl --fail --output "$existing_links" "${links_url}?per_page=100"; then
    echo "ERROR: release link already exists but could not be inspected: $expected_name" >&2
    return 1
  fi
  python3 - "$existing_links" "$expected_name" "$expected_url" "$expected_path" "$expected_type" <<'PY'
import json
import sys
from pathlib import Path

links_file, name, url, direct_asset_path, link_type = sys.argv[1:6]
links = json.loads(Path(links_file).read_text())
matches = [link for link in links if link.get("name") == name]
if len(matches) != 1:
    raise SystemExit(f"expected one existing release link named {name!r}, found {len(matches)}")
link = matches[0]
actual = (link.get("url"), link.get("link_type"))
expected = (url, link_type)
direct_asset_url = link.get("direct_asset_url") or ""
if actual != expected or not direct_asset_url.endswith(f"/downloads{direct_asset_path}"):
    raise SystemExit(f"existing release link {name!r} does not match the requested asset")
PY
  echo "Reusing identical release link: $expected_name"
}

publish_gitlab_release() {
  if ! require_supported_env CI_API_V4_URL CI_PROJECT_ID CI_PROJECT_URL CI_JOB_TOKEN; then
    return 0
  fi

  local tag_name notes_file asset_dir package_name
  tag_name="$(release_tag)"
  notes_file="${MAGISK_RELEASE_NOTES_FILE:-release-notes.md}"
  asset_dir="${MAGISK_GITLAB_RELEASE_ASSET_DIR:-release-assets}"
  package_name="${MAGISK_GITLAB_RELEASE_PACKAGE_NAME:-mipush-release}"

  generate_release_notes "$tag_name" "$notes_file" gitlab

  collect_and_validate_release_assets "$tag_name"
  copy_assets "$asset_dir" "${release_assets[@]}"

  MAGISK_RELEASE_NOTES_FILE="$notes_file" \
  MAGISK_RELEASE_NAME="$tag_name" \
  MAGISK_GITLAB_RELEASE_PACKAGE_NAME="$package_name" \
  MAGISK_GITLAB_RELEASE_PREPARED_ASSET_DIR="$asset_dir" \
  MAGISK_GITLAB_RELEASE_DIRECT_ASSET_PREFIX="/mipush" \
    bash "$MAGISK_CI_TOOLKIT_DIR/release/gitlab_release.sh"
}

mode="${1:-}"
cd "$ROOT_DIR"

case "$mode" in
  release-notes)
    notes_file="${MAGISK_RELEASE_NOTES_FILE:-release-notes.md}"
    generate_release_notes "$(release_tag)" "$notes_file" "${MAGISK_RELEASE_PLATFORM:-source}"
    echo "Generated release notes: $notes_file"
    ;;
  validate-assets)
    current_tag="$(release_tag)"
    collect_and_validate_release_assets "$current_tag"
    echo "Validated release asset set: $current_tag"
    ;;
  gitlab-release)
    publish_gitlab_release
    ;;
  -h|--help|"")
    usage
    [[ -n "$mode" ]] && exit 0 || exit 2
    ;;
  *)
    echo "ERROR: unknown mode: $mode" >&2
    usage
    exit 2
    ;;
esac
