#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
selector="$(cd "$script_dir/../../.." && pwd)/scripts/ci/select_android_test_tasks.sh"
tmp_dir="$(mktemp -d)"; trap 'rm -rf "$tmp_dir"' EXIT
assert_contains() { grep -Fx -- "$1" "$2" >/dev/null || { echo "missing $1" >&2; exit 1; }; }
assert_empty() { [[ ! -s "$1" ]] || { echo "expected empty output" >&2; exit 1; }; }
printf 'impact\ncommon/Example.kt\n' > "$tmp_dir/common"
bash "$selector" /dev/null "$tmp_dir/common" > "$tmp_dir/out"
assert_contains ':common:check' "$tmp_dir/out"; assert_contains ':xmsf:testNormalDebugUnitTest' "$tmp_dir/out"
printf 'impact\nxposed/Example.kt\n' > "$tmp_dir/xposed"
bash "$selector" /dev/null "$tmp_dir/xposed" > "$tmp_dir/out"; assert_contains ':xposed:testDebugUnitTest' "$tmp_dir/out"
printf 'impact\ndocs/ci.md\n' > "$tmp_dir/docs"
bash "$selector" /dev/null "$tmp_dir/docs" > "$tmp_dir/out"; assert_empty "$tmp_dir/out"
printf 'full\n' > "$tmp_dir/full"
bash "$selector" /dev/null "$tmp_dir/full" > "$tmp_dir/out"; assert_contains 'qualityGateKoverVerify' "$tmp_dir/out"
echo 'MiPush selector tests passed'
