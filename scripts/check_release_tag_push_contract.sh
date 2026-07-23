#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_SCRIPT="$SCRIPT_DIR/release_tag.sh"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

[[ -f "$RELEASE_SCRIPT" ]] || fail "release_tag.sh is missing"

# A GitLab release must suppress the branch pipeline, while the tag push must
# remain a normal push so the tag pipeline is created.
grep -Fq 'git -C "$ROOT_DIR" push -o ci.skip "$REMOTE_NAME" "$current_branch"' "$RELEASE_SCRIPT" \
  || fail "branch push does not use GitLab ci.skip"
grep -Fq 'git -C "$ROOT_DIR" push "$REMOTE_NAME" "$TAG_NAME"' "$RELEASE_SCRIPT" \
  || fail "tag push is missing"

if grep -Fq 'git -C "$ROOT_DIR" push "$REMOTE_NAME" "$current_branch"' "$RELEASE_SCRIPT"; then
  fail "an unsuppressed branch push is still present"
fi
if grep -Fq 'git -C "$ROOT_DIR" push -o ci.skip "$REMOTE_NAME" "$TAG_NAME"' "$RELEASE_SCRIPT"; then
  fail "ci.skip must not be applied to the tag push"
fi

echo "PASS: release tag push contract"
