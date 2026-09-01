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

check_branch_state_before_checks() {
  local head_commit remote_branch_commit

  head_commit="$(git -C "$ROOT_DIR" rev-parse HEAD)"
  release_head_commit="$head_commit"
  if ! remote_branch_commit="$(git -C "$ROOT_DIR" ls-remote "$REMOTE_NAME" "refs/heads/$current_branch" | awk 'NR == 1 {print $1}')"; then
    echo "ERROR: failed to query remote branch $REMOTE_NAME/$current_branch" >&2
    exit 1
  fi
  release_remote_branch_commit="$remote_branch_commit"
  release_force_push_required=false

  if [[ -z "$remote_branch_commit" || "$remote_branch_commit" == "$head_commit" ]]; then
    return 0
  fi

  if git -C "$ROOT_DIR" merge-base --is-ancestor "$remote_branch_commit" "$head_commit" 2>/dev/null; then
    return 0
  fi

  release_force_push_required=true
  echo "WARN: remote branch $current_branch is not fast-forwardable; will use --force-with-lease." >&2
  echo "WARN: expected remote branch HEAD: $remote_branch_commit" >&2
}

push_release_branch() {
  local force_push_enabled=false

  restore_force_push_protection() {
    if [[ "$force_push_enabled" == true ]]; then
      echo "Restoring protected-branch force-push protection: $current_branch"
      set_protected_branch_force_push false
    fi
  }

  if [[ "$release_force_push_required" == true ]]; then
    if ! command -v glab >/dev/null 2>&1; then
      echo "ERROR: glab is required to temporarily allow force push on protected branch $current_branch." >&2
      exit 1
    fi
    echo "Temporarily allowing force push on protected branch: $current_branch"
    set_protected_branch_force_push true
    force_push_enabled=true
    trap restore_force_push_protection EXIT
  fi

  git -C "$ROOT_DIR" push --force-with-lease="refs/heads/$current_branch:$release_remote_branch_commit" \
    -o ci.skip "$REMOTE_NAME" "$current_branch"

  restore_force_push_protection
  force_push_enabled=false
  trap - EXIT
}

set_protected_branch_force_push() {
  local allow_force_push="$1"
  glab api --method PATCH \
    "projects/:fullpath/protected_branches/$current_branch" \
    --field "allow_force_push=$allow_force_push" \
    >/dev/null
}

extract_toml_value() {
  local key="$1"
  local file="$2"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$file" | head -n1
}

run_pre_push_checks() {
  local gradle_args=(
    --warning-mode all
    :common:compileDebugKotlin
    :xmsf:runtime:compileDebugKotlin
    :xmsf:shell:assembleNormalDebug
    :xmsf:shell:assembleVc105Debug
    :xmsf:assembleRelease
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
    bash "$ROOT_DIR/scripts/ci/tests/test_select_android_test_tasks.sh"
    bash "$ROOT_DIR/scripts/ci/tests/test_run_test_shards.sh"
    MAGISK_CI_TOOLKIT_DIR="$TOOLKIT_DIR" bash "$ROOT_DIR/scripts/ci/run_test_shards.sh" \
      android-pure-modules
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

check_branch_state_before_checks
"$ROOT_DIR/scripts/check_release_guard.sh" "$TAG_NAME"
run_pre_push_checks

if [[ "$(git -C "$ROOT_DIR" rev-parse HEAD)" != "$release_head_commit" ]]; then
  echo "ERROR: HEAD changed during release checks; refusing to create tag." >&2
  exit 1
fi

if working_tree_dirty; then
  echo "ERROR: working tree is not clean. Commit/stash changes before tagging." >&2
  exit 1
fi

if git -C "$ROOT_DIR" rev-parse -q --verify "refs/tags/$TAG_NAME" >/dev/null; then
  echo "Deleting local tag: $TAG_NAME"
  git -C "$ROOT_DIR" tag -d "$TAG_NAME" >/dev/null
fi

remote_output="$(git -C "$ROOT_DIR" ls-remote --tags "$REMOTE_NAME" \
  "refs/tags/$TAG_NAME" "refs/tags/$TAG_NAME^{}")"
if [[ -n "$remote_output" ]]; then
  echo "Deleting remote tag: $TAG_NAME"
  git -C "$ROOT_DIR" push "$REMOTE_NAME" ":refs/tags/$TAG_NAME"
fi

# The branch commit is already validated locally; do not create a second CI
# pipeline when the subsequent tag push will start the release pipeline.
push_release_branch
git -C "$ROOT_DIR" tag -s "$TAG_NAME" -m "$TAG_NAME"
git -C "$ROOT_DIR" push --force "$REMOTE_NAME" "$TAG_NAME"

echo "Pushed release tag: $TAG_NAME (branch: $current_branch, remote: $REMOTE_NAME)"
