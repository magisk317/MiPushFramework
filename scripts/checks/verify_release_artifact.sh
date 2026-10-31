#!/usr/bin/env bash
# Verifies the release-packaging contract that the root path and the runtime log depend on:
#
#   1. libsu classes survive R8. They are reached only reflectively, so an unkept build shrinks
#      Shell$Builder / Shell$Result / internal.JobTask and PrivilegeElevator.tryToElevate()
#      throws NoClassDefFoundError in every release build.
#   2. Classes implementing StableLogTag keep their binary names, because the release log tag is
#      javaClass.simpleName — a renamed class turns the tag into an opaque R8 short name.
#
# Usage: scripts/checks/verify_release_artifact.sh <path-to-release-apk>
# Idempotent: reads the APK and the working tree, writes nothing outside a temp directory.
set -euo pipefail

APK="${1:-}"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "usage: $0 <path-to-release-apk>" >&2
  exit 2
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

unzip -q -o "$APK" 'classes*.dex' -d "$WORK"
if ! ls "$WORK"/classes*.dex >/dev/null 2>&1; then
  echo "FAIL: no dex found in $APK" >&2
  exit 1
fi

missing=()

present_in_dex() {
  local descriptor="$1"
  grep -aqF "$descriptor" "$WORK"/classes*.dex
}

for descriptor in \
  "Lcom/topjohnwu/superuser/Shell\$Builder;" \
  "Lcom/topjohnwu/superuser/Shell\$Result;" \
  "Lcom/topjohnwu/superuser/internal/JobTask;" \
  "Lcom/topjohnwu/superuser/internal/ShellInputSource;"
do
  present_in_dex "$descriptor" || missing+=("libsu $descriptor")
done

# Names are taken from the annotated declarations so a newly marked class is covered without
# editing here. A file can host several types, so the name comes from the declaration directly
# below the marker, never from the file name.
stable_tag_classes=()
while IFS= read -r source; do
  [[ -z "$source" ]] && continue
  package="$(grep -m1 -E '^package ' "$source" | awk '{print $2}')"
  [[ -z "$package" ]] && continue
  while IFS= read -r class; do
    [[ -z "$class" ]] && continue
    stable_tag_classes+=("${package}.${class}")
  done < <(
    grep -A1 -E '^@StableLogTag$' "$source" |
      grep -E '(class|object) [A-Za-z0-9_]+' |
      sed -E 's/.*(class|object) ([A-Za-z0-9_]+).*/\2/'
  )
done < <(
  grep -rl --include="*.kt" "^@StableLogTag$" \
    "$REPO_ROOT/xmsf" "$REPO_ROOT/common" "$REPO_ROOT/vendor" 2>/dev/null |
    grep -v "/build/" | grep -v "/src/test/" || true
)

# An empty list would make the name check pass without checking anything.
if ((${#stable_tag_classes[@]} == 0)); then
  echo "FAIL: no @StableLogTag declarations found under xmsf/common/vendor" >&2
  exit 1
fi

for qualified in "${stable_tag_classes[@]}"; do
  descriptor="L${qualified//.//};"
  present_in_dex "$descriptor" || missing+=("stable tag $descriptor")
done

if ((${#missing[@]} > 0)); then
  echo "FAIL: release artifact contract broken for $(basename "$APK")" >&2
  printf '  - %s\n' "${missing[@]}" >&2
  echo "Check the R8 keep rules in xmsf/proguard-rules.pro (and xmsf/shell/proguard-rules.pro)." >&2
  exit 1
fi

echo "OK: libsu classes and ${#stable_tag_classes[@]} @StableLogTag names are present in $(basename "$APK")"
