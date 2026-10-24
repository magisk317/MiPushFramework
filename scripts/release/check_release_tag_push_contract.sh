#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TOOLKIT_DIR="$("$ROOT_DIR/scripts/resolve_ci_toolkit.sh")"

# The push contract is implemented by the toolkit, so that is what has to be
# asserted — checking the project wrapper would only re-check a delegation.
RELEASE_SCRIPT="${TOOLKIT_DIR}/release/release_tag.sh"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

[[ -f "$RELEASE_SCRIPT" ]] || fail "toolkit release_tag.sh is missing ($RELEASE_SCRIPT)"

# A GitLab release must suppress the branch pipeline, while the tag push must
# remain a normal push so the tag pipeline is created. Force options are
# allowed because release_tag.sh may rewrite an amended release commit.
# These patterns intentionally match literal shell source, including the variable names.
# shellcheck disable=SC2016
grep -Fq -- '-o ci.skip "$remote_name" "$current_branch"' "$RELEASE_SCRIPT" \
  || fail "branch push does not use GitLab ci.skip"
# shellcheck disable=SC2016
grep -Fq -- 'push --force "$remote_name" "$tag_name"' "$RELEASE_SCRIPT" \
  || fail "tag push is missing"

# shellcheck disable=SC2016
if grep -Fq -- 'ci.skip "$remote_name" "$tag_name"' "$RELEASE_SCRIPT"; then
  fail "ci.skip must not be applied to the tag push"
fi

# The branch push must be lease-protected against the remote commit observed
# before the checks ran, so a branch that moved meanwhile cannot be clobbered.
# shellcheck disable=SC2016
grep -Fq -- '--force-with-lease="refs/heads/$current_branch:${release_remote_branch_commit}"' "$RELEASE_SCRIPT" \
  || fail "branch push must lease against the observed remote commit"
# shellcheck disable=SC2016
grep -Fq -- 'check_branch_state_before_checks "$root_dir" "$remote_name" "$current_branch"' "$RELEASE_SCRIPT" \
  || fail "release_tag must snapshot the branch state before running the checks"

echo "PASS: release tag push contract"
