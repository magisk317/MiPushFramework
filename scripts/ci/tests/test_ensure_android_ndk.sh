#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
installer="$root_dir/scripts/ci/ensure_android_ndk.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf -- "$tmp_dir"' EXIT

mkdir -p "$tmp_dir/bin" "$tmp_dir/sdk/ndk/30.0.16248370" "$tmp_dir/sdk/.locks" "$tmp_dir/sdk/.downloads/ndk"

write_source_properties() {
  local destination="$1"
  local revision="$2"
  local release="$3"
  cat > "$destination" <<EOF
Pkg.Desc = Android NDK
Pkg.Revision = $revision
Pkg.BaseRevision = $revision
Pkg.ReleaseName = $release
EOF
}

write_fake_ndk() {
  local directory="$1"
  mkdir -p "$directory/toolchains/llvm/prebuilt/linux-x86_64/bin"
  printf '#!/usr/bin/env sh\nexit 0\n' > "$directory/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
  chmod +x "$directory/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
  printf '#!/usr/bin/env sh\nexit 0\n' > "$directory/ndk-build"
  chmod +x "$directory/ndk-build"
  write_source_properties "$directory/source.properties" 30.0.16248370 r30
}

write_fake_ndk "$tmp_dir/sdk/ndk/30.0.16248370"

cat > "$tmp_dir/bin/mountpoint" <<'FAKE_MOUNT'
#!/usr/bin/env bash
exit 0
FAKE_MOUNT
cat > "$tmp_dir/bin/awk" <<'FAKE_AWK'
#!/usr/bin/env bash
if [[ "${1:-}" == -v && "${2:-}" == mount=* ]]; then
  shift 2
  shift
  exec /usr/bin/awk 'BEGIN { exit 0 }'
fi
exec /usr/bin/awk "$@"
FAKE_AWK
cat > "$tmp_dir/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$FAKE_CURL_LOG"
args=("$@")
out=""
for ((i=0; i<${#args[@]}; i++)); do
  if [[ "${args[i]}" == --output ]]; then out="${args[i+1]}"; fi
done
: "${out:?missing output path}"
if [[ "${FAKE_CURL_MODE:-ok}" == fail-before ]]; then
  exit 22
fi
if [[ "${FAKE_CURL_MODE:-ok}" == fail-partial ]]; then
  printf 'partial archive data' > "$out"
  exit 56
fi
tmp="$(mktemp -d)"
mkdir -p "$tmp/android-ndk-r30/toolchains/llvm/prebuilt/linux-x86_64/bin"
printf '#!/usr/bin/env sh\nexit 0\n' > "$tmp/android-ndk-r30/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
chmod +x "$tmp/android-ndk-r30/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
printf '#!/usr/bin/env sh\nexit 0\n' > "$tmp/android-ndk-r30/ndk-build"
chmod +x "$tmp/android-ndk-r30/ndk-build"
revision="${FAKE_ARCHIVE_REVISION:-30.0.16248370}"
release="${FAKE_ARCHIVE_RELEASE:-r30}"
cat > "$tmp/android-ndk-r30/source.properties" <<EOF
Pkg.Desc = Android NDK
Pkg.Revision = $revision
Pkg.BaseRevision = $revision
Pkg.ReleaseName = $release
EOF
mkdir -p "$(dirname "$out")"
(cd "$tmp" && zip -qr "$tmp/archive.zip" android-ndk-r30)
cp "$tmp/archive.zip" "$out"
rm -rf -- "$tmp"
FAKE_CURL
chmod +x "$tmp_dir/bin/mountpoint" "$tmp_dir/bin/awk" "$tmp_dir/bin/curl"

run_installer() {
  env PATH="$tmp_dir/bin:/usr/bin:/bin" \
    ANDROID_SDK_ROOT="$tmp_dir/sdk" \
    ANDROID_HOME="$tmp_dir/sdk" \
    MIPUSH_ZYGISK_NDK_VERSION=r30 \
    MIPUSH_ZYGISK_NDK_REVISION=30.0.16248370 \
    MIPUSH_ZYGISK_NDK_HOME="$tmp_dir/sdk/ndk/30.0.16248370" \
    FAKE_CURL_LOG="$tmp_dir/curl.log" \
    "$@" bash "$installer"
}

assert_no_target() {
  [[ ! -e "$tmp_dir/sdk/ndk/30.0.16248370" ]] || {
    printf 'FAIL %s: incomplete NDK target was left behind\n' "$1" >&2
    exit 1
  }
}

# A validated persistent side-by-side install is reused without a download.
run_installer
[[ ! -s "$tmp_dir/curl.log" ]] || { printf 'FAIL reuse: installer downloaded despite valid target\n' >&2; exit 1; }
printf 'PASS reuse\n'

# A bad existing target is not silently trusted or overwritten.
write_source_properties "$tmp_dir/sdk/ndk/30.0.16248370/source.properties" 30.0.16138531 r30-beta3
if run_installer; then printf 'FAIL bad-existing: invalid target was reused\n' >&2; exit 1; fi
write_fake_ndk "$tmp_dir/sdk/ndk/30.0.16248370"
printf 'PASS reject-mismatched-existing\n'

# Download failures must not leave an installed directory; partial bytes remain resumable.
rm -rf "$tmp_dir/sdk/ndk/30.0.16248370"
if run_installer env FAKE_CURL_MODE=fail-before; then printf 'FAIL fail-before: expected download failure\n' >&2; exit 1; fi
assert_no_target fail-before
[[ ! -e "$tmp_dir/sdk/.downloads/ndk/android-ndk-r30-linux.zip.partial" ]] || {
  printf 'FAIL fail-before: empty partial file was retained\n' >&2; exit 1;
}
printf 'PASS fail-before\n'
if run_installer env FAKE_CURL_MODE=fail-partial; then printf 'FAIL fail-partial: expected download failure\n' >&2; exit 1; fi
assert_no_target fail-partial
[[ -s "$tmp_dir/sdk/.downloads/ndk/android-ndk-r30-linux.zip.partial" ]] || {
  printf 'FAIL fail-partial: resumable partial archive was not kept\n' >&2; exit 1;
}
printf 'PASS fail-partial\n'

# A valid archive installs atomically under the SDK NDK path and is reused next time.
rm -f "$tmp_dir/sdk/.downloads/ndk/android-ndk-r30-linux.zip.partial"
run_installer
[[ -x "$tmp_dir/sdk/ndk/30.0.16248370/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] || {
  printf 'FAIL install: NDK clang is missing\n' >&2; exit 1;
}
/usr/bin/grep -Fxq 'Pkg.ReleaseName = r30' "$tmp_dir/sdk/ndk/30.0.16248370/source.properties"
run_installer
printf 'PASS atomic-install-and-reuse\n'

# Archive metadata mismatch is rejected before an install can become visible.
rm -rf "$tmp_dir/sdk/ndk/30.0.16248370"
if run_installer env FAKE_ARCHIVE_REVISION=30.0.16138531 FAKE_ARCHIVE_RELEASE=r30-beta3; then
  printf 'FAIL wrong-archive: mismatched archive was installed\n' >&2
  exit 1
fi
assert_no_target wrong-archive
printf 'PASS reject-wrong-archive\n'

# Curl must explicitly bypass the runner proxy for dl.google.com.
grep -F -- '--noproxy *' "$tmp_dir/curl.log" >/dev/null || {
  printf 'FAIL direct-download: curl did not request command-level proxy bypass\n' >&2
  exit 1;
}
printf 'All Android NDK cache tests passed\n'
