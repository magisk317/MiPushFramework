#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
CHANGELOG_FILE="$ROOT_DIR/docs/CHANGELOG.md"
TAG_NAME="${1:-}"
ALLOW_NON_ASCII_COMMIT_SUBJECT="${ALLOW_NON_ASCII_COMMIT_SUBJECT:-false}"

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "ERROR: Missing $VERSION_FILE" >&2
  exit 2
fi

VERSION_NAME="$(extract_toml_value "versionName" "$VERSION_FILE")"
VERSION_CODE="$(extract_toml_value "versionCode" "$VERSION_FILE")"
if [[ -z "$VERSION_NAME" ]]; then
  echo "ERROR: Failed to parse versionName from $VERSION_FILE" >&2
  exit 2
fi

echo "Release guard"
echo "- versionName: $VERSION_NAME"
if [[ -n "$VERSION_CODE" ]]; then
  echo "- versionCode: $VERSION_CODE"
fi
echo "- commit subject ascii-only: $([[ "$ALLOW_NON_ASCII_COMMIT_SUBJECT" == "true" ]] && echo "disabled" || echo "enabled")"

FAIL=0

check_non_ascii_commit_subjects() {
  local commit_range=""
  local base_tag=""
  local checked=0
  local has_non_ascii=0
  local row=""
  local sha=""
  local subject=""
  local offenders=()

  base_tag="$(git -C "$ROOT_DIR" describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)"
  if [[ -n "$base_tag" ]]; then
    commit_range="$base_tag..HEAD"
  else
    commit_range="HEAD"
  fi

  while IFS=$'\t' read -r sha subject; do
    [[ -z "$sha" ]] && continue
    checked=$((checked + 1))
    if printf '%s' "$subject" | LC_ALL=C grep -q '[^ -~]'; then
      has_non_ascii=1
      offenders+=("$sha|$subject")
    fi
  done < <(git -C "$ROOT_DIR" log --no-merges --pretty=format:'%h%x09%s' "$commit_range")

  if (( checked == 0 )); then
    echo "PASS: commit subject check skipped (no commits in range: $commit_range)"
    return
  fi

  if (( has_non_ascii == 0 )); then
    echo "PASS: commit subjects are ASCII-only ($checked commits, range: $commit_range)"
    return
  fi

  echo "FAIL: non-ASCII commit subject detected (range: $commit_range)"
  for row in "${offenders[@]}"; do
    sha="${row%%|*}"
    subject="${row#*|}"
    echo " - $sha $subject"
  done
  FAIL=1
}

if [[ "$ALLOW_NON_ASCII_COMMIT_SUBJECT" == "true" ]]; then
  echo "PASS: commit subject ASCII guard disabled by ALLOW_NON_ASCII_COMMIT_SUBJECT=true"
else
  check_non_ascii_commit_subjects
fi

if [[ ! -f "$CHANGELOG_FILE" ]]; then
  echo "FAIL: missing changelog file ($CHANGELOG_FILE)"
  FAIL=1
elif grep -Fq "## [v$VERSION_NAME]" "$CHANGELOG_FILE"; then
  echo "PASS: changelog contains section for v$VERSION_NAME"
else
  echo "FAIL: changelog section not found: ## [v$VERSION_NAME] in $CHANGELOG_FILE"
  FAIL=1
fi

if [[ -n "$TAG_NAME" ]]; then
  expected_tag="v$VERSION_NAME"
  if [[ "$TAG_NAME" != "$expected_tag" ]]; then
    echo "FAIL: tag mismatch. got '$TAG_NAME', expected '$expected_tag' from versionName."
    FAIL=1
  else
    echo "PASS: tag matches versionName ($TAG_NAME)"
  fi
fi

if (( FAIL != 0 )); then
  echo "Release guard failed." >&2
  exit 1
fi

echo "Release guard passed."
