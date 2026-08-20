#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"
TOOLKIT_SCRIPT="${TOOLKIT_DIR}/release/check_release_guard.sh"

# MiPushFramework configuration
TAG_NAME="${1:-}"

"$SCRIPT_DIR/check_release_tag_push_contract.sh"

check_non_ascii_subject_allowlist() {
  local release_commit="HEAD"
  local base_tag=""
  local commit_range=""
  local sha=""
  local subject=""
  local fail=0
  base_tag="$(git -C "$ROOT_DIR" describe --tags --abbrev=0 --match 'v*' "${release_commit}^" 2>/dev/null || true)"
  if [[ -z "$base_tag" ]]; then
    base_tag="$(git -C "$ROOT_DIR" tag -l --sort=-creatordate 'v*' | grep -v "^${TAG_NAME}$" | head -n 1 || true)"
  fi
  commit_range="$release_commit"
  if [[ -n "$base_tag" ]] && git -C "$ROOT_DIR" rev-parse -q --verify "refs/tags/$base_tag" >/dev/null; then
    commit_range="refs/tags/$base_tag..$release_commit"
  fi

  while IFS=$'\t' read -r sha subject; do
    [[ -n "$sha" ]] || continue
    if ! printf '%s' "$subject" | LC_ALL=C grep -q '[^ -~]'; then
      continue
    fi
    case "$sha" in
      3924dbe05f482a5ac5ec4223a456b3f8413825eb)
        echo "PASS: historical non-ASCII commit subject is allowlisted ($sha)"
        ;;
      *)
        echo "FAIL: non-ASCII commit subject requires review ($sha): $subject" >&2
        fail=1
        ;;
    esac
  done < <(git -C "$ROOT_DIR" log --no-merges --pretty=format:'%H%x09%s' "$commit_range")

  return "$fail"
}

check_non_ascii_subject_allowlist
source "${TOOLKIT_SCRIPT}"
ALLOW_NON_ASCII_COMMIT_SUBJECT=true check_release_guard "$ROOT_DIR" "$TAG_NAME"
"${ROOT_DIR}/scripts/check_zygisk_version_sync.sh" "$ROOT_DIR"
