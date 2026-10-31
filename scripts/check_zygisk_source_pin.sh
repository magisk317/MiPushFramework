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

url="${MIPUSH_ZYGISK_REPOSITORY:-https://gitlab.com/magisk3171/MiPushZygisk.git}"
probe="$(mktemp -d)"
trap 'rm -rf "$probe"' EXIT
if git -C "$probe" init -q 2>/dev/null \
  && git -C "$probe" fetch --quiet --depth 1 "$url" "$ref" 2>/dev/null; then
  echo "PASS: Zygisk source pin ${ref:0:12} reachable on MiPushZygisk"
else
  echo "FAIL: Zygisk source pin ${ref:0:12} not reachable on $url" >&2
  exit 1
fi
