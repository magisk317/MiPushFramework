#!/usr/bin/env bash
set -euo pipefail

# The release ships the Zygisk module from the pinned MiPushZygisk commit; a
# pin that the remote cannot serve breaks the tag pipeline's Zygisk build, so
# the guard proves reachability before anything pushes. ls-remote only lists
# ref tips, and the pin is usually a mid-branch commit, so the probe is a
# fetch-by-SHA (GitLab serves reachable SHAs on request) — the same method the
# pointer gate uses for gitlinks.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ref="$(sed -nE 's/^[[:space:]]*MIPUSH_ZYGISK_REF:[[:space:]]*"([0-9a-fA-F]{40})".*/\1/p' "$ROOT_DIR/.gitlab-ci.yml" | head -n1)"
if [[ -z "$ref" ]]; then
  echo "FAIL: MIPUSH_ZYGISK_REF is not pinned to a 40-hex SHA in .gitlab-ci.yml" >&2
  exit 1
fi

# Every literal declaration of this pin must be the same commit: the GitLab
# variable, both GitHub workflow envs and the resolver's fallback default.
# They named three different faces of the pin before this comparison existed
# -- GitLab moved to a new Zygisk commit while the workflows and the resolver
# fallback still pointed at the previous one for a whole release.
mapfile -t pin_declarations < <(
  grep -RInE '(MIPUSH_ZYGISK_REF|default_zygisk_ref)(:[[:space:]]*"?|:-)[0-9a-fA-F]' \
    "$ROOT_DIR/.gitlab-ci.yml" "$ROOT_DIR/.github" "$ROOT_DIR/scripts" 2>/dev/null
)
(( ${#pin_declarations[@]} >= 1 )) || {
  echo "FAIL: no literal zygisk pin declarations found; the scan pattern rotted" >&2
  exit 1
}
ref_lower="$(printf '%s' "$ref" | tr '[:upper:]' '[:lower:]')"
for declaration in "${pin_declarations[@]}"; do
  location="${declaration%%:*}"
  hexes="$(printf '%s\n' "$declaration" | grep -oE '[0-9a-fA-F]{40}' || true)"
  [[ -n "$hexes" ]] || {
    echo "FAIL: zygisk pin declaration in $location is not a full 40-hex SHA: $declaration" >&2
    exit 1
  }
  while read -r hex; do
    [[ -n "$hex" ]] || continue
    hex_lower="$(printf '%s' "$hex" | tr '[:upper:]' '[:lower:]')"
    [[ "$hex_lower" == "$ref_lower" ]] || {
      echo "FAIL: zygisk pin drift in $location: $hex != $ref (declared in .gitlab-ci.yml)" >&2
      exit 1
    }
  done <<< "$hexes"
done
echo "PASS: ${#pin_declarations[@]} zygisk pin declarations agree on ${ref:0:12}"

url="${MIPUSH_ZYGISK_REPOSITORY:-https://gitlab.com/magisk3171/MiPushZygisk.git}"
probe="$(mktemp -d)"
# The EXIT trap must not poison the exit status: on overlayfs the rm can
# fail with "Directory not empty" and turn a passing check into a failure.
trap 'rm -rf "$probe" 2>/dev/null || true' EXIT
if git -C "$probe" init -q 2>/dev/null \
  && git -C "$probe" fetch --quiet --depth 1 "$url" "$ref" 2>/dev/null; then
  echo "PASS: Zygisk source pin ${ref:0:12} reachable on MiPushZygisk"
else
  echo "FAIL: Zygisk source pin ${ref:0:12} not reachable on $url" >&2
  exit 1
fi
