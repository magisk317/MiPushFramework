#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
selector="$root_dir/scripts/ci/select_android_test_tasks.sh"
# shellcheck source=../android_test_shards.sh
source "$root_dir/scripts/ci/android_test_shards.sh"
tmp_dir="$(mktemp -d)"; trap 'rm -rf "$tmp_dir"' EXIT
assert_contains() { grep -Fx -- "$1" "$2" >/dev/null || { echo "missing $1" >&2; exit 1; }; }
assert_empty() { [[ ! -s "$1" ]] || { echo "expected empty output" >&2; exit 1; }; }
assert_known_shards() {
  while IFS= read -r shard; do
    [[ -z "$shard" ]] || is_android_test_shard "$shard" || {
      echo "selector emitted unknown shard: $shard" >&2
      exit 1
    }
  done < "$1"
}
printf 'impact\ncommon/Example.kt\n' > "$tmp_dir/common"
bash "$selector" /dev/null "$tmp_dir/common" > "$tmp_dir/out"
assert_contains 'android-pure-modules' "$tmp_dir/out"; assert_known_shards "$tmp_dir/out"
printf 'impact\nxposed/Example.kt\n' > "$tmp_dir/xposed"
bash "$selector" /dev/null "$tmp_dir/xposed" > "$tmp_dir/out"
assert_contains 'android-pure-modules' "$tmp_dir/out"; assert_known_shards "$tmp_dir/out"
printf 'impact\napp/Example.kt\nmipush/Example.kt\n' > "$tmp_dir/apps"
bash "$selector" /dev/null "$tmp_dir/apps" > "$tmp_dir/out"
assert_contains 'app-compile' "$tmp_dir/out"; assert_contains 'mipush-compile' "$tmp_dir/out"
assert_known_shards "$tmp_dir/out"
printf 'impact\ndocs/ci.md\n' > "$tmp_dir/docs"
bash "$selector" /dev/null "$tmp_dir/docs" > "$tmp_dir/out"; assert_empty "$tmp_dir/out"
printf 'full\n' > "$tmp_dir/full"
bash "$selector" /dev/null "$tmp_dir/full" > "$tmp_dir/out"
for shard in "${ANDROID_TEST_SHARDS[@]}"; do assert_contains "$shard" "$tmp_dir/out"; done
assert_known_shards "$tmp_dir/out"
echo 'MiPush selector tests passed'
