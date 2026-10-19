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
# remain a normal push so the tag pipeline is created. Force options are
# allowed because release_tag.sh may rewrite an amended release commit.
# These patterns intentionally match literal shell source, including the variable names.
# shellcheck disable=SC2016
grep -Fq -- '-o ci.skip "$REMOTE_NAME" "$current_branch"' "$RELEASE_SCRIPT" \
  || fail "branch push does not use GitLab ci.skip"
# shellcheck disable=SC2016
grep -Fq -- '"$REMOTE_NAME" "$TAG_NAME"' "$RELEASE_SCRIPT" \
  || fail "tag push is missing"

# shellcheck disable=SC2016
if grep -Fq -- '-o ci.skip "$REMOTE_NAME" "$TAG_NAME"' "$RELEASE_SCRIPT"; then
  fail "ci.skip must not be applied to the tag push"
fi

echo "PASS: release tag push contract"
