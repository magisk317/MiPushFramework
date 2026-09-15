#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CI_FILE="$ROOT_DIR/.gitlab-ci.yml"
RESOLVER_FILE="$ROOT_DIR/scripts/resolve_ci_toolkit.sh"
ACTION_FILE="$ROOT_DIR/.github/actions/resolve-ci-toolkit/action.yml"
TOOLKIT_PROJECT="magisk3171/shared/magisk-ci-toolkit"
SHA_PATTERN='^[0-9a-f]{40}$'

fail() {
  echo "CI toolkit reference check failed: $*" >&2
  exit 1
}

normalize_sha() {
  tr '[:upper:]' '[:lower:]'
}

ci_variable_ref="$(sed -nE 's/^[[:space:]]*MAGISK_CI_TOOLKIT_REF:[[:space:]]*"?([0-9a-fA-F]{40})"?[[:space:]]*$/\1/p' "$CI_FILE")"
[ "$(printf '%s\n' "$ci_variable_ref" | sed '/^$/d' | wc -l)" -eq 1 ] ||
  fail "expected exactly one MAGISK_CI_TOOLKIT_REF declaration"
ci_variable_ref="$(printf '%s' "$ci_variable_ref" | normalize_sha)"
[[ "$ci_variable_ref" =~ $SHA_PATTERN ]] || fail "MAGISK_CI_TOOLKIT_REF must be a full SHA"

mapfile -t include_refs < <(
  awk -v project="$TOOLKIT_PROJECT" '
    $0 ~ "^[[:space:]]*-[[:space:]]+project:[[:space:]]*" project "[[:space:]]*$" {
      awaiting_ref = 1
      next
    }
    awaiting_ref && $0 ~ "^[[:space:]]*ref:[[:space:]]*" {
      sub(/^[[:space:]]*ref:[[:space:]]*/, "")
      gsub(/[[:space:]]/, "")
      print
      awaiting_ref = 0
      next
    }
    awaiting_ref && $0 ~ "^[[:space:]]*-[[:space:]]+project:" {
      exit 2
    }
  ' "$CI_FILE"
) || fail "a toolkit include is missing its ref"

[ "${#include_refs[@]}" -eq 5 ] ||
  fail "expected five $TOOLKIT_PROJECT include refs, found ${#include_refs[@]}"
for ref in "${include_refs[@]}"; do
  normalized_ref="$(printf '%s' "$ref" | normalize_sha)"
  [[ "$normalized_ref" =~ $SHA_PATTERN ]] || fail "include ref must be a full SHA: $ref"
  [ "$normalized_ref" = "$ci_variable_ref" ] ||
    fail "include ref $normalized_ref differs from MAGISK_CI_TOOLKIT_REF $ci_variable_ref"
done

# The resolver script and the GitHub composite action both carry the same
# fallback SHA; keeping them in sync with MAGISK_CI_TOOLKIT_REF is what makes
# local scripts and CI resolve the same toolkit commit.
resolver_ref="$(sed -nE 's/^TOOLKIT_REF="\$\{MAGISK_CI_TOOLKIT_REF:-([0-9a-fA-F]{40})\}"$/\1/p' "$RESOLVER_FILE")"
[ -n "$resolver_ref" ] || fail "no resolver fallback SHA found in $RESOLVER_FILE"
resolver_ref="$(printf '%s' "$resolver_ref" | normalize_sha)"
[[ "$resolver_ref" =~ $SHA_PATTERN ]] || fail "resolver fallback must be a full SHA"
[ "$resolver_ref" = "$ci_variable_ref" ] ||
  fail "resolver fallback $resolver_ref differs from MAGISK_CI_TOOLKIT_REF $ci_variable_ref"

action_ref="$(sed -nE 's/^[[:space:]]*default: .([0-9a-fA-F]{40}).$/\1/p' "$ACTION_FILE")"
[ -n "$action_ref" ] || fail "no default ref found in $ACTION_FILE"
action_ref="$(printf '%s' "$action_ref" | normalize_sha)"
[ "$action_ref" = "$ci_variable_ref" ] ||
  fail "composite action default ref $action_ref differs from MAGISK_CI_TOOLKIT_REF $ci_variable_ref"

printf 'CI toolkit reference verified: %s (%s includes)\n' "$ci_variable_ref" "${#include_refs[@]}"
