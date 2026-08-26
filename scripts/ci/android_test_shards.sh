#!/usr/bin/env bash

# Shared protocol for scripts that select or execute Android CI test shards.
readonly -a ANDROID_TEST_SHARDS=(
  android-pure-modules
  app-compile
  mipush-compile
)

print_android_test_shards() {
  printf '%s\n' "${ANDROID_TEST_SHARDS[@]}"
}

is_android_test_shard() {
  local candidate="$1"
  local shard
  for shard in "${ANDROID_TEST_SHARDS[@]}"; do
    [[ "$candidate" == "$shard" ]] && return 0
  done
  return 1
}
