#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"

working_tree_dirty() {
  if ! git -C "$ROOT_DIR" diff --quiet || ! git -C "$ROOT_DIR" diff --cached --quiet; then
    return 0
  fi
  [[ -n "$(git -C "$ROOT_DIR" status --porcelain)" ]]
}

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

run_pre_push_checks() {
  echo "Running pre-push CI command..."
  (
    cd "$ROOT_DIR"
    bash scripts/with_workspace_gradle_lock.sh --warning-mode all \
      :common:check \
      :xmsf:assembleDebug \
      :xmsf:testDebugUnitTest \
      -PbuildSplits \
      -Pkotlin.incremental=false
  )
  echo "Pre-push checks passed."
}

VERSION_NAME="$(extract_toml_value "versionName" "$VERSION_FILE")"
if [[ -z "$VERSION_NAME" ]]; then
  echo "ERROR: failed to parse versionName from $VERSION_FILE" >&2
  exit 2
fi

TAG_NAME="v$VERSION_NAME"
REMOTE_NAME="${RELEASE_REMOTE:-origin}"

current_branch="$(git -C "$ROOT_DIR" branch --show-current)"
if [[ -z "$current_branch" ]]; then
  echo "ERROR: detached HEAD is not supported for release_tag.sh" >&2
  exit 1
fi

"$ROOT_DIR/scripts/check_release_guard.sh" "$TAG_NAME"
run_pre_push_checks

if working_tree_dirty; then
  echo "ERROR: working tree is not clean. Commit/stash changes before tagging." >&2
  exit 1
fi

delete_local_tag_if_exists() {
  if git -C "$ROOT_DIR" rev-parse -q --verify "refs/tags/$TAG_NAME" >/dev/null; then
    local old_ref
    old_ref="$(git -C "$ROOT_DIR" rev-list -n 1 "$TAG_NAME" 2>/dev/null || true)"
    echo "WARN: local tag exists, deleting before retag: $TAG_NAME (${old_ref:-unknown})"
    git -C "$ROOT_DIR" tag -d "$TAG_NAME" >/dev/null
  fi
}

delete_remote_tag_if_exists() {
  local remote_output
  local remote_ref
  if ! remote_output="$(git -C "$ROOT_DIR" ls-remote --tags "$REMOTE_NAME" "refs/tags/$TAG_NAME")"; then
    echo "ERROR: failed to query remote tags from $REMOTE_NAME" >&2
    exit 1
  fi
  remote_ref="$(printf '%s\n' "$remote_output" | awk '{print $1}' | head -n1)"
  if [[ -n "$remote_ref" ]]; then
    echo "WARN: remote tag exists, deleting before retag: $TAG_NAME ($remote_ref)"
    if ! git -C "$ROOT_DIR" push "$REMOTE_NAME" ":refs/tags/$TAG_NAME"; then
      echo "ERROR: failed to delete remote tag $TAG_NAME from $REMOTE_NAME" >&2
      exit 1
    fi
  fi
}

delete_local_tag_if_exists
delete_remote_tag_if_exists

git -C "$ROOT_DIR" tag -s "$TAG_NAME" -m "$TAG_NAME"
git -C "$ROOT_DIR" push --force-with-lease "$REMOTE_NAME" "$current_branch"
git -C "$ROOT_DIR" push "$REMOTE_NAME" "$TAG_NAME"

echo "Created and pushed tag: $TAG_NAME (branch: $current_branch, remote: $REMOTE_NAME)"
