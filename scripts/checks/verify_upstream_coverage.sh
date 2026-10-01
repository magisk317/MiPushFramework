#!/usr/bin/env bash
set -euo pipefail

# Verifies that every upstream change is already present in a local branch by content rather than
# by SHA.
#
# This tree descends from the same lineage as upstream, but a past migration appended a trailing
# newline to the message of every inherited commit, which rewrote each SHA down the chain. As a
# result `git rev-list --count HEAD..upstream/master` reports a large phantom backlog even though
# nothing upstream is actually missing. Patch ids are content hashes and are immune to that, so
# they answer the only question that matters here: is anything upstream still missing?
#
# Usage:
#   scripts/checks/verify_upstream_coverage.sh [upstream-ref] [local-ref]
#
# Defaults to `upstream/master` and `HEAD`. The upstream ref must already be fetched; this script
# never touches the network.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

UPSTREAM_REF="${1:-upstream/master}"
LOCAL_REF="${2:-HEAD}"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

git rev-parse --verify --quiet "${UPSTREAM_REF}^{commit}" >/dev/null \
  || fail "$UPSTREAM_REF is not a commit; fetch it first (git fetch upstream)"
git rev-parse --verify --quiet "${LOCAL_REF}^{commit}" >/dev/null \
  || fail "$LOCAL_REF is not a commit"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# `git log -p` emits "commit <sha>" markers, which patch-id turns into "<patch-id> <sha>" pairs.
git log -p --no-merges --format='commit %H' "$UPSTREAM_REF" \
  | git patch-id --stable > "$TMP_DIR/upstream.pairs"
git log -p --no-merges --format='commit %H' "$LOCAL_REF" \
  | git patch-id --stable > "$TMP_DIR/local.pairs"

awk '{ print $1 }' "$TMP_DIR/upstream.pairs" | sort -u > "$TMP_DIR/upstream.ids"
awk '{ print $1 }' "$TMP_DIR/local.pairs" | sort -u > "$TMP_DIR/local.ids"

# Keep the upstream commit that owns each unmatched patch so the report can name it.
awk 'NR == FNR { known[$1] = 1; next } !($1 in known) { print $2 }' \
  "$TMP_DIR/local.ids" "$TMP_DIR/upstream.pairs" \
  | sort -u > "$TMP_DIR/missing.sha"

upstream_total="$(wc -l < "$TMP_DIR/upstream.ids" | tr -d ' ')"
missing_total="$(wc -l < "$TMP_DIR/missing.sha" | tr -d ' ')"

echo "upstream ref      : $UPSTREAM_REF"
echo "local ref         : $LOCAL_REF"
echo "upstream patches  : $upstream_total"
echo "unmatched patches : $missing_total"

if [[ "$missing_total" == "0" ]]; then
  echo "PASS: every upstream change is already present in $LOCAL_REF by content"
  exit 0
fi

echo
echo "Upstream commits with no content match in $LOCAL_REF:"
while read -r sha; do
  [[ -n "$sha" ]] || continue
  git log -1 --format='  %h %ad %s' --date=short "$sha"
done < "$TMP_DIR/missing.sha"

fail "$missing_total upstream change(s) are missing from $LOCAL_REF"
