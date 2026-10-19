#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"
export MAGISK_RELEASE_POLICY_FILE="$ROOT_DIR/scripts/config/release-policy.json"
POLICY_FILE="$MAGISK_RELEASE_POLICY_FILE"
[[ -f "$POLICY_FILE" ]] || { echo "ERROR: release policy is missing: $POLICY_FILE" >&2; exit 1; }

# Accept both "<tag>" (CI invocation) and "<root-dir> <tag>" (toolkit entry
# point convention).
if [[ -n "${1:-}" ]] && [[ -d "$1" ]]; then
  TAG_NAME="${2:-}"
else
  TAG_NAME="${1:-}"
fi

# MiPushFramework project check. The policy sets
# allow_non_ascii_commit_subject=true, which disables the toolkit's blanket
# ASCII guard; this check keeps the stricter per-commit allowlist on top of it.
# Dispatched by the toolkit via project_checks, so it receives
# <root-dir> <version-name> <tag-name>.
check_non_ascii_subject_allowlist() {
  local root_dir="$1"
  local tag_name="${3:-}"
  local release_commit="HEAD"
  local base_tag=""
  local commit_range=""
  local sha=""
  local subject=""
  local fail=0

  base_tag="$(git -C "$root_dir" describe --tags --abbrev=0 --match 'v*' "${release_commit}^" 2>/dev/null || true)"
  if [[ -z "$base_tag" ]]; then
    # In CI shallow tag clones, fetch all tags from origin to reconstruct tag topology
    git -C "$root_dir" fetch --tags --force origin >/dev/null 2>&1 || true
    base_tag="$(git -C "$root_dir" describe --tags --abbrev=0 --match 'v*' "${release_commit}^" 2>/dev/null || true)"
  fi
  if [[ -z "$base_tag" ]]; then
    base_tag="$(git -C "$root_dir" tag -l --sort=-creatordate 'v*' | grep -v "^${tag_name}$" | head -n 1 || true)"
  fi
  if [[ -z "$base_tag" && -f "$root_dir/docs/CHANGELOG.md" && -n "$tag_name" ]]; then
    base_tag="$(sed -nE "s#.*compare/([^[:space:]/]+)\.\.\.${tag_name}.*#\1#p" "$root_dir/docs/CHANGELOG.md" | head -n 1)"
  fi
  commit_range="$release_commit"
  if [[ -n "$base_tag" ]] && git -C "$root_dir" rev-parse -q --verify "refs/tags/$base_tag^{commit}" >/dev/null 2>&1; then
    commit_range="refs/tags/$base_tag..$release_commit"
  elif [[ -n "$base_tag" ]] && git -C "$root_dir" rev-parse -q --verify "$base_tag" >/dev/null 2>&1; then
    commit_range="$base_tag..$release_commit"
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
  done < <(git -C "$root_dir" log --no-merges --pretty=format:'%H%x09%s' "$commit_range")

  return "$fail"
}

# The release guard is supplied by the externally resolved CI toolkit.
# shellcheck source=check_release_guard.sh
source "${TOOLKIT_DIR}/release/check_release_guard.sh"
check_release_guard "$ROOT_DIR" "$TAG_NAME"
