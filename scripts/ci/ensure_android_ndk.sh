#!/usr/bin/env bash
# Ensure the pinned Android NDK exists in the runner's persistent SDK mount.
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android-sdk}}"
NDK_VERSION="${MIPUSH_ZYGISK_NDK_VERSION:-r30}"
NDK_REVISION="${MIPUSH_ZYGISK_NDK_REVISION:-30.0.16248370}"
NDK_HOME="${MIPUSH_ZYGISK_NDK_HOME:-$SDK_ROOT/ndk/$NDK_REVISION}"

case "$NDK_VERSION:$NDK_REVISION" in
  r30:30.0.16248370)
    EXPECTED_RELEASE="r30"
    ARCHIVE_ROOT="android-ndk-r30"
    ARCHIVE_NAME="android-ndk-r30-linux.zip"
    ;;
  *)
    echo "ERROR: unsupported Android NDK release mapping: $NDK_VERSION:$NDK_REVISION" >&2
    exit 2
    ;;
esac

if [[ "$NDK_HOME" != "$SDK_ROOT/ndk/$NDK_REVISION" ]]; then
  echo "ERROR: NDK home must use the SDK side-by-side path: $SDK_ROOT/ndk/$NDK_REVISION" >&2
  exit 2
fi
if [[ ! -d "$SDK_ROOT" || ! -w "$SDK_ROOT" ]]; then
  echo "ERROR: persistent Android SDK mount is missing or not writable: $SDK_ROOT" >&2
  exit 1
fi
if [[ "$SDK_ROOT" == "/opt/android-sdk" ]] && ! awk -v mount="$SDK_ROOT" '$5 == mount { found = 1 } END { exit !found }' /proc/self/mountinfo; then
  echo "ERROR: $SDK_ROOT is not a mounted SDK volume; refusing to download into the job container" >&2
  exit 1
fi
for command_name in curl unzip flock awk; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "ERROR: required command is unavailable: $command_name" >&2
    exit 1
  fi
done

mkdir -p "$SDK_ROOT/.locks" "$SDK_ROOT/.downloads/ndk"
LOCK_FILE="$SDK_ROOT/.locks/mipush-zygisk-ndk-$NDK_REVISION.lock"
exec 9>"$LOCK_FILE"
flock -x 9

read_property() {
  local properties_file="$1"
  local property_name="$2"
  awk -F= -v wanted="$property_name" '
    function trim(value) {
      sub(/^[[:space:]]+/, "", value)
      sub(/[[:space:]]+$/, "", value)
      return value
    }
    {
      separator = index($0, "=")
      if (separator == 0) next
      key = trim(substr($0, 1, separator - 1))
      if (key == wanted) {
        count++
        value = trim(substr($0, separator + 1))
      }
    }
    END {
      if (count != 1) exit 1
      print value
    }
  ' "$properties_file"
}

validate_install() {
  local ndk_dir="$1"
  local revision release_name
  [[ -f "$ndk_dir/source.properties" ]] || return 1
  [[ -x "$ndk_dir/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] || return 1
  [[ -x "$ndk_dir/ndk-build" ]] || return 1
  revision="$(read_property "$ndk_dir/source.properties" Pkg.Revision)" || return 1
  release_name="$(read_property "$ndk_dir/source.properties" Pkg.ReleaseName)" || return 1
  [[ "$revision" == "$NDK_REVISION" && "$release_name" == "$EXPECTED_RELEASE" ]]
}

if [[ -e "$NDK_HOME" ]]; then
  if [[ -L "$NDK_HOME" ]]; then
    echo "ERROR: NDK target is a symlink; refusing to reuse it: $NDK_HOME" >&2
    exit 1
  fi
  if validate_install "$NDK_HOME"; then
    printf 'Reusing persistent Android NDK %s (%s) at %s\n' "$NDK_VERSION" "$NDK_REVISION" "$NDK_HOME"
    exit 0
  fi
  echo "ERROR: existing NDK directory failed version/completeness validation; refusing to reuse or overwrite: $NDK_HOME" >&2
  exit 1
fi

# A forced job termination can bypass EXIT traps. Remove only our own hidden
# staging directories; the resumable archive is deliberately kept separately.
for stale_dir in "$SDK_ROOT"/.ndk-install-"$NDK_REVISION".*; do
  if [[ -d "$stale_dir" ]]; then
    rm -rf -- "$stale_dir"
  fi
done

PARTIAL_ZIP="$SDK_ROOT/.downloads/ndk/$ARCHIVE_NAME.partial"
ARCHIVE_URL="https://dl.google.com/android/repository/$ARCHIVE_NAME"
if [[ -s "$PARTIAL_ZIP" ]] && unzip -tq "$PARTIAL_ZIP" >/dev/null; then
  echo "Reusing complete NDK archive from the persistent SDK cache"
else
  printf 'Downloading Android NDK %s (%s) directly into the persistent SDK cache\n' "$NDK_VERSION" "$NDK_REVISION"
  if [[ ! -e "$PARTIAL_ZIP" ]]; then
    : > "$PARTIAL_ZIP"
  fi
  if ! curl --noproxy '*' \
    --retry "${MIPUSH_ZYGISK_NDK_DOWNLOAD_RETRIES:-5}" \
    --retry-all-errors \
    --retry-delay 2 \
    --connect-timeout 20 \
    --max-time "${MIPUSH_ZYGISK_NDK_DOWNLOAD_MAX_TIME:-1800}" \
    --continue-at - \
    --fail --location --silent --show-error \
    --output "$PARTIAL_ZIP" \
    "$ARCHIVE_URL"; then
    if [[ ! -s "$PARTIAL_ZIP" ]]; then
      rm -f -- "$PARTIAL_ZIP"
      echo "ERROR: NDK download failed before any resumable data was received" >&2
      exit 1
    fi
    if ! unzip -tq "$PARTIAL_ZIP" >/dev/null; then
      echo "ERROR: NDK download failed; keeping the partial archive for a resumable retry" >&2
      exit 1
    fi
    echo "Download reported an error after writing a complete NDK archive; validating the archive"
  fi
fi

if [[ ! -s "$PARTIAL_ZIP" ]] || ! unzip -tq "$PARTIAL_ZIP" >/dev/null; then
  rm -f -- "$PARTIAL_ZIP"
  echo "ERROR: downloaded NDK archive is empty or failed ZIP integrity validation" >&2
  exit 1
fi

if ! archive_entries="$(unzip -Z1 "$PARTIAL_ZIP")" || [[ -z "$archive_entries" ]]; then
  rm -f -- "$PARTIAL_ZIP"
  echo "ERROR: could not read NDK archive entries" >&2
  exit 1
fi
while IFS= read -r entry; do
  [[ -n "$entry" ]] || continue
  if [[ "$entry" == /* || "$entry" == ".." || "$entry" == ../* || "$entry" == */../* || "$entry" == */.. ]]; then
    rm -f -- "$PARTIAL_ZIP"
    echo "ERROR: unsafe path in NDK archive: $entry" >&2
    exit 1
  fi
  case "$entry" in
    "$ARCHIVE_ROOT"|"$ARCHIVE_ROOT"/*) ;;
    *)
      rm -f -- "$PARTIAL_ZIP"
      echo "ERROR: unexpected top-level path in NDK archive: $entry" >&2
      exit 1
      ;;
  esac
done <<< "$archive_entries"
source_entry_count="$(printf '%s\n' "$archive_entries" | grep -Fxc "$ARCHIVE_ROOT/source.properties" || true)"
if [[ "$source_entry_count" != "1" ]]; then
  rm -f -- "$PARTIAL_ZIP"
  echo "ERROR: NDK archive must contain exactly one $ARCHIVE_ROOT/source.properties" >&2
  exit 1
fi

STAGED_PROPERTIES="$SDK_ROOT/.downloads/ndk/source.properties.$$.tmp"
if ! unzip -p "$PARTIAL_ZIP" "$ARCHIVE_ROOT/source.properties" > "$STAGED_PROPERTIES"; then
  rm -f -- "$PARTIAL_ZIP" "$STAGED_PROPERTIES"
  echo "ERROR: failed to read source.properties from NDK archive" >&2
  exit 1
fi
archive_revision="$(read_property "$STAGED_PROPERTIES" Pkg.Revision || true)"
archive_release="$(read_property "$STAGED_PROPERTIES" Pkg.ReleaseName || true)"
rm -f -- "$STAGED_PROPERTIES"
if [[ "$archive_revision" != "$NDK_REVISION" || "$archive_release" != "$EXPECTED_RELEASE" ]]; then
  rm -f -- "$PARTIAL_ZIP"
  echo "ERROR: NDK archive metadata mismatch: expected $EXPECTED_RELEASE/$NDK_REVISION, got ${archive_release:-<missing>}/${archive_revision:-<missing>}" >&2
  exit 1
fi

STAGE_DIR="$(mktemp -d "$SDK_ROOT/.ndk-install-$NDK_REVISION.XXXXXX")"
cleanup_stage() {
  if [[ -n "${STAGE_DIR:-}" && -d "$STAGE_DIR" ]]; then
    rm -rf -- "$STAGE_DIR"
  fi
}
trap cleanup_stage EXIT
if ! unzip -q "$PARTIAL_ZIP" -d "$STAGE_DIR"; then
  rm -f -- "$PARTIAL_ZIP"
  echo "ERROR: failed to extract validated NDK archive" >&2
  exit 1
fi
EXTRACTED_NDK="$STAGE_DIR/$ARCHIVE_ROOT"
if ! validate_install "$EXTRACTED_NDK"; then
  rm -f -- "$PARTIAL_ZIP"
  echo "ERROR: extracted NDK failed version/completeness validation" >&2
  exit 1
fi

mkdir -p "$SDK_ROOT/ndk"
if [[ -e "$NDK_HOME" ]]; then
  echo "ERROR: NDK target appeared while the install lock was held; refusing to overwrite: $NDK_HOME" >&2
  exit 1
fi
mv -- "$EXTRACTED_NDK" "$NDK_HOME"
rm -rf -- "$STAGE_DIR"
STAGE_DIR=""
rm -f -- "$PARTIAL_ZIP"
printf 'Installed Android NDK %s (%s) at %s\n' "$NDK_VERSION" "$NDK_REVISION" "$NDK_HOME"
