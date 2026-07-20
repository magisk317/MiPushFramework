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
  local gradle_args=(
    --warning-mode all
    :common:check
    :xmsf:assembleNormalDebug
    :xmsf:assembleVc105Debug
    :xmsf:testNormalDebugUnitTest
    :xmsf:testVc105DebugUnitTest
    :app:assembleRelease
    :mipush:assembleRelease
    -PbuildSplits
    -Pkotlin.incremental=false
  )

  if release_skip_detekt; then
    echo "WARN: RELEASE_TAG_SKIP_DETEKT is enabled; detekt tasks will be skipped."
    gradle_args+=(-x detekt -x qualityGateDetekt)
  else
    echo "Detekt checks are blocking. Set RELEASE_TAG_SKIP_DETEKT=1 to bypass them."
  fi

  echo "Running pre-push CI command..."
  (
    cd "$ROOT_DIR"
    TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"
    bash "$TOOLKIT_DIR/gradle/run_gradle_with_retry.sh" "${gradle_args[@]}"
  )
  echo "Pre-push checks passed."
}

release_skip_detekt() {
  case "${RELEASE_TAG_SKIP_DETEKT:-}" in
    1|true|TRUE|yes|YES|on|ON)
      return 0
      ;;
    ""|0|false|FALSE|no|NO|off|OFF)
      return 1
      ;;
    *)
      echo "ERROR: RELEASE_TAG_SKIP_DETEKT must be 1/true/yes/on or 0/false/no/off." >&2
      exit 2
      ;;
  esac
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

local_tag_exists=false
if git -C "$ROOT_DIR" rev-parse -q --verify "refs/tags/$TAG_NAME" >/dev/null; then
  local_tag_exists=true
  local_tag_commit="$(git -C "$ROOT_DIR" rev-parse "refs/tags/$TAG_NAME^{commit}")"
  head_commit="$(git -C "$ROOT_DIR" rev-parse HEAD)"
  if [[ "$local_tag_commit" != "$head_commit" ]]; then
    echo "ERROR: local tag $TAG_NAME points to $local_tag_commit, not HEAD $head_commit" >&2
    exit 1
  fi
  if [[ "$(git -C "$ROOT_DIR" cat-file -t "refs/tags/$TAG_NAME")" != tag ]]; then
    echo "ERROR: local tag $TAG_NAME is not an annotated signed tag" >&2
    exit 1
  fi
  if ! git -C "$ROOT_DIR" verify-tag "$TAG_NAME" >/dev/null 2>&1; then
    echo "ERROR: local tag $TAG_NAME does not have a verifiable signature" >&2
    exit 1
  fi
  echo "Reusing existing local tag after a previous push failure: $TAG_NAME"
fi

remote_output=""
if ! remote_output="$(git -C "$ROOT_DIR" ls-remote --tags "$REMOTE_NAME" \
  "refs/tags/$TAG_NAME" "refs/tags/$TAG_NAME^{}")"; then
    echo "ERROR: failed to query remote tags from $REMOTE_NAME" >&2
    exit 1
fi
if [[ -n "$remote_output" ]]; then
  echo "ERROR: remote tag $TAG_NAME already exists and is immutable; retry its GitLab pipeline instead of retagging" >&2
  exit 1
fi

git -C "$ROOT_DIR" push "$REMOTE_NAME" "$current_branch"
if [[ "$local_tag_exists" != true ]]; then
  git -C "$ROOT_DIR" tag -s "$TAG_NAME" -m "$TAG_NAME"
fi
git -C "$ROOT_DIR" push "$REMOTE_NAME" "$TAG_NAME"

echo "Pushed release tag: $TAG_NAME (branch: $current_branch, remote: $REMOTE_NAME)"
