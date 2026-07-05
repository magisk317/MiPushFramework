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
  publish_mipush_release.sh gitlab-release
  publish_mipush_release.sh modules-repo

Modes:
  gitlab-release  Publish XMSF APK, MiPush APK, Zygisk zip, and debug files to GitLab Release.
  modules-repo    Publish release APKs to Xposed-Modules-Repo.
EOF
}

urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "ERROR: $name is required" >&2
    exit 2
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
  local generate_github_notes="${3:-false}"
  local source_repo="${MAGISK_ANDROID_RELEASE_GITHUB_REPOSITORY:-magisk317/MiPushFramework}"
  local changelog_file="${MAGISK_RELEASE_CHANGELOG_FILE:-docs/CHANGELOG.md}"
  local changelog_extract generated_notes previous_tag compare_range
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

  generated_notes=""
  if [[ "$generate_github_notes" == "true" && -n "$source_repo" ]] && command -v gh >/dev/null 2>&1; then
    previous_tag=""
    compare_range="$(sed -nE 's#.*compare/([^[:space:]]+)\.\.\.([^[:space:]]+).*#\1 \2#p' "$changelog_extract" | head -n 1)"
    if [[ -n "$compare_range" ]]; then
      read -r previous_tag compare_to <<< "$compare_range"
      if [[ "$compare_to" != "$tag_name" ]]; then
        previous_tag=""
      fi
    fi
    if [[ -z "$previous_tag" ]]; then
      previous_tag="$(git describe --tags --abbrev=0 "${tag_name}^{commit}^" 2>/dev/null || true)"
    fi
    generate_args=(-X POST -f "tag_name=${tag_name}")
    if [[ -n "$previous_tag" ]]; then
      generate_args+=(-f "previous_tag_name=${previous_tag}")
    fi
    generated_notes="$(gh api "repos/${source_repo}/releases/generate-notes" \
      "${generate_args[@]}" \
      --jq '.body' 2>/dev/null || true)"
    if [[ -n "$generated_notes" ]]; then
      sed -i '/^[[:space:]]*>[[:space:]]*Full Changelog:/d' "$changelog_extract"
    fi
  fi

  cat "$changelog_extract" > "$output_file"
  if [[ -n "$generated_notes" ]]; then
    printf '\n%s\n' "$generated_notes" >> "$output_file"
  fi
}

find_if_dir() {
  local dir="$1"
  shift
  if [[ -d "$dir" ]]; then
    find "$dir" "$@"
  fi
}

collect_main_assets() {
  {
    find_if_dir app/build/outputs/apk -type f -path '*/release/*.apk'
    find_if_dir mipush/build/outputs/apk -type f -path '*/release/*.apk'
    find_if_dir MiPushZygisk/build -type f -name '*.zip'
    find_if_dir app/build/outputs/mapping -type f -name mapping.txt
    find_if_dir app/build/outputs/native-debug-symbols -type f -name native-debug-symbols.zip
  } | sort
}

collect_modules_repo_assets() {
  {
    find_if_dir app/build/outputs/apk -type f -path '*/release/*.apk'
    find_if_dir mipush/build/outputs/apk -type f -path '*/release/*.apk'
  } | sort
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

publish_gitlab_release() {
  require_env CI_API_V4_URL
  require_env CI_PROJECT_ID
  require_env CI_PROJECT_URL
  require_env CI_JOB_TOKEN

  local tag_name notes_file asset_dir package_name encoded_project encoded_tag encoded_package
  tag_name="$(release_tag)"
  notes_file="${MAGISK_RELEASE_NOTES_FILE:-release-notes.md}"
  asset_dir="${MAGISK_GITLAB_RELEASE_ASSET_DIR:-release-assets}"
  package_name="${MAGISK_GITLAB_RELEASE_PACKAGE_NAME:-mipush-release}"

  generate_release_notes "$tag_name" "$notes_file" false

  mapfile -t release_assets < <(collect_main_assets)
  if [[ "${#release_assets[@]}" -eq 0 ]]; then
    echo "ERROR: no release assets found" >&2
    exit 1
  fi
  copy_assets "$asset_dir" "${release_assets[@]}"

  encoded_project="$(urlencode "$CI_PROJECT_ID")"
  encoded_tag="$(urlencode "$tag_name")"
  encoded_package="$(urlencode "$package_name")"

  make_tmp_file links_json
  make_tmp_file payload_json
  make_tmp_file update_json

  printf '[\n' > "$links_json"
  first_link=true
  for asset_path in "$asset_dir"/*; do
    asset_name="$(basename "$asset_path")"
    encoded_asset="$(urlencode "$asset_name")"
    package_url="${CI_API_V4_URL}/projects/${encoded_project}/packages/generic/${encoded_package}/${encoded_tag}/${encoded_asset}"
    download_url="${CI_PROJECT_URL}/-/packages/generic/${package_name}/${tag_name}/${asset_name}"

    upload_status="$(
      gitlab_curl --output /tmp/mipush-gitlab-package-upload.json --write-out "%{http_code}" \
        --request PUT \
        --upload-file "$asset_path" \
        "$package_url" || true
    )"
    case "$upload_status" in
      200|201|409) ;;
      400)
        if ! grep -qiE 'already|taken|exist' /tmp/mipush-gitlab-package-upload.json; then
          echo "ERROR: failed to upload $asset_name (HTTP $upload_status)" >&2
          cat /tmp/mipush-gitlab-package-upload.json >&2 || true
          exit 1
        fi
        ;;
      *)
        echo "ERROR: failed to upload $asset_name (HTTP $upload_status)" >&2
        cat /tmp/mipush-gitlab-package-upload.json >&2 || true
        exit 1
        ;;
    esac

    if [[ "$first_link" != true ]]; then
      printf ',\n' >> "$links_json"
    fi
    first_link=false
    python3 - "$asset_name" "$download_url" "/mipush/${asset_name}" >> "$links_json" <<'PY'
import json
import sys

name, url, direct_asset_path = sys.argv[1:4]
print(json.dumps({
    "name": name,
    "url": url,
    "direct_asset_path": direct_asset_path,
    "link_type": "package",
}))
PY
  done
  printf '\n]\n' >> "$links_json"

  python3 - "$tag_name" "$notes_file" "$links_json" > "$payload_json" <<'PY'
import json
import sys
from pathlib import Path

tag_name, notes_file, links_file = sys.argv[1:4]
print(json.dumps({
    "name": tag_name,
    "tag_name": tag_name,
    "description": Path(notes_file).read_text(),
    "assets": {"links": json.loads(Path(links_file).read_text())},
}))
PY

  python3 - "$tag_name" "$notes_file" > "$update_json" <<'PY'
import json
import sys
from pathlib import Path

tag_name, notes_file = sys.argv[1:3]
print(json.dumps({
    "name": tag_name,
    "description": Path(notes_file).read_text(),
}))
PY

  release_url="${CI_API_V4_URL}/projects/${encoded_project}/releases/${encoded_tag}"
  create_url="${CI_API_V4_URL}/projects/${encoded_project}/releases"
  status="$(gitlab_curl --output /tmp/mipush-gitlab-release-get.json --write-out "%{http_code}" "$release_url" || true)"
  case "$status" in
    200)
      gitlab_curl --fail \
        --request PUT \
        --header "Content-Type: application/json" \
        --data @"$update_json" \
        "$release_url" >/dev/null

      links_url="${release_url}/assets/links"
      python3 - "$links_json" > /tmp/mipush-gitlab-release-links.tsv <<'PY'
import json
import sys
from pathlib import Path

for link in json.loads(Path(sys.argv[1]).read_text()):
    print("\t".join([link["name"], link["url"], link["direct_asset_path"], link["link_type"]]))
PY
      while IFS=$'\t' read -r link_name link_url direct_asset_path link_type; do
        link_status="$(
          gitlab_curl --output /tmp/mipush-gitlab-release-link.json --write-out "%{http_code}" \
            --request POST \
            --data-urlencode "name=${link_name}" \
            --data-urlencode "url=${link_url}" \
            --data-urlencode "direct_asset_path=${direct_asset_path}" \
            --data "link_type=${link_type}" \
            "$links_url" || true
        )"
        case "$link_status" in
          201|409) ;;
          *)
            echo "ERROR: failed to create release link $link_name (HTTP $link_status)" >&2
            cat /tmp/mipush-gitlab-release-link.json >&2 || true
            exit 1
            ;;
        esac
      done < /tmp/mipush-gitlab-release-links.tsv
      ;;
    404)
      gitlab_curl --fail \
        --request POST \
        --header "Content-Type: application/json" \
        --data @"$payload_json" \
        "$create_url" >/dev/null
      ;;
    *)
      echo "ERROR: failed to inspect GitLab release $tag_name (HTTP $status)" >&2
      cat /tmp/mipush-gitlab-release-get.json >&2 || true
      exit 1
      ;;
  esac

  echo "Published GitLab release: $tag_name"
}

publish_modules_repo() {
  require_env MAGISK_ANDROID_RELEASE_MODULES_REPO
  require_env XPOSED_MODULES_REPO_TOKEN

  if ! command -v gh >/dev/null 2>&1; then
    echo "ERROR: gh is required for modules repo publication" >&2
    exit 2
  fi

  local tag_name notes_file source_token
  tag_name="$(release_tag)"
  notes_file="${MAGISK_RELEASE_NOTES_FILE:-release-notes.md}"
  source_token="${SOURCE_RELEASE_TOKEN:-$XPOSED_MODULES_REPO_TOKEN}"

  GH_TOKEN="$source_token" generate_release_notes "$tag_name" "$notes_file" true

  mapfile -t apk_files < <(collect_modules_repo_assets)
  if [[ "${#apk_files[@]}" -eq 0 ]]; then
    echo "ERROR: no APK files found for modules repo publication" >&2
    exit 1
  fi

  export GH_TOKEN="$XPOSED_MODULES_REPO_TOKEN"
  if gh release view "$tag_name" --repo "$MAGISK_ANDROID_RELEASE_MODULES_REPO" >/dev/null 2>&1; then
    gh release upload "$tag_name" "${apk_files[@]}" \
      --repo "$MAGISK_ANDROID_RELEASE_MODULES_REPO" \
      --clobber
    gh release edit "$tag_name" \
      --repo "$MAGISK_ANDROID_RELEASE_MODULES_REPO" \
      --title "$tag_name" \
      --draft=false \
      --notes-file "$notes_file"
  else
    gh release create "$tag_name" "${apk_files[@]}" \
      --repo "$MAGISK_ANDROID_RELEASE_MODULES_REPO" \
      --title "$tag_name" \
      --notes-file "$notes_file"
  fi

  echo "Published Xposed-Modules-Repo release: $tag_name"
}

mode="${1:-}"
cd "$ROOT_DIR"

case "$mode" in
  gitlab-release)
    publish_gitlab_release
    ;;
  modules-repo)
    publish_modules_repo
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
