#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
runner="$root_dir/scripts/ci/run_test_shards.sh"
# shellcheck source=../android_test_shards.sh
source "$root_dir/scripts/ci/android_test_shards.sh"
tmp_dir="$(mktemp -d)"; trap 'rm -rf "$tmp_dir"' EXIT
mkdir -p "$tmp_dir/toolkit/gradle"
cat > "$tmp_dir/toolkit/gradle/run_gradle_with_retry.sh" <<'FAKE'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$SHARD_LOG"
FAKE
chmod +x "$tmp_dir/toolkit/gradle/run_gradle_with_retry.sh"
export SHARD_LOG="$tmp_dir/gradle.log"
MAGISK_CI_TOOLKIT_DIR="$tmp_dir/toolkit" bash "$runner" "${ANDROID_TEST_SHARDS[@]}"
grep -F ':app:compileNormalDebugKotlin' "$SHARD_LOG" >/dev/null
grep -F ':mipush:compileGithubDebugKotlin' "$SHARD_LOG" >/dev/null
grep -F ':mipush:compilePlayDebugKotlin' "$SHARD_LOG" >/dev/null
grep -F ':core:jvmTest' "$SHARD_LOG" >/dev/null
grep -F ':configuration:jvmTest' "$SHARD_LOG" >/dev/null
grep -F ':xmsf:platform:jvmTest' "$SHARD_LOG" >/dev/null
grep -F ':xmsf:runtime:store:testAndroidHostTest' "$SHARD_LOG" >/dev/null
if grep -F ':mipush:compileDebugKotlin' "$SHARD_LOG" >/dev/null; then
  echo 'runner still invokes ambiguous mipush Debug compile task' >&2
  exit 1
fi
if grep -F ':core:testDebugUnitTest' "$SHARD_LOG" >/dev/null; then
  echo 'runner still invokes obsolete core Android unit-test task' >&2
  exit 1
fi
if grep -F ':configuration:testDebugUnitTest' "$SHARD_LOG" >/dev/null; then
  echo 'runner still invokes obsolete configuration Android unit-test task' >&2
  exit 1
fi
if grep -F ':xmsf:platform:testDebugUnitTest' "$SHARD_LOG" >/dev/null; then
  echo 'runner still invokes obsolete xmsf/platform Android unit-test task' >&2
  exit 1
fi
if MAGISK_CI_TOOLKIT_DIR="$tmp_dir/toolkit" bash "$runner" unknown-shard >/dev/null 2>&1; then
  echo 'runner accepted an unknown shard' >&2
  exit 1
fi
echo 'MiPush shard runner tests passed'
