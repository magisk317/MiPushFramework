#!/usr/bin/env bash
# Keeps scripts/config/android-test-tasks.json honest against two things it cannot see:
# the shard names run_test_shards.sh actually implements, and the unit of work GitLab's
# android:test:pure-modules runs. Both drifted silently before; this is the same
# pin-with-a-test move as test_stage_ci_dist.sh made for the allocated versionCode.
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
config="$root_dir/scripts/config/android-test-tasks.json"
# shellcheck source=../android_test_shards.sh
# shellcheck disable=SC1091
source "$root_dir/scripts/ci/android_test_shards.sh"

[[ -f "$config" ]] || { echo "missing $config" >&2; exit 1; }

# Command substitution, not `mapfile < <(...)`: with the process-substitution form the
# python exit status is dropped on the floor and a rejected config would sail through.
if ! referenced_raw="$(python3 - "$config" "$root_dir" <<'PY'
import json
import sys

config_path, root_dir = sys.argv[1], sys.argv[2]
data = json.load(open(config_path, encoding="utf-8"))
for key in ("full_tasks", "full_paths", "ignore_paths", "rules"):
    if key not in data:
        raise SystemExit("selector config missing %s" % key)
if data.get("unknown") != "full":
    raise SystemExit('unknown must be "full", so an unmapped path escalates instead of silently skipping tests')
if data["full_tasks"] != ["android-pure-modules"]:
    raise SystemExit(
        "full_tasks must be exactly the shard GitLab's android:test:pure-modules runs, got %r"
        % (data["full_tasks"],))
if data["full_paths"] != [
    "build.gradle*", "settings.gradle*", "gradle.properties", "gradle/*",
    "build-logic/*", ".gitmodules", "scripts/*", ".magisk-ci-toolkit/*",
]:
    raise SystemExit("full_paths changed; a build-definition edit must still select everything, "
                     "so check GitLab before editing this list")

names = set(data["full_tasks"])
for rule in data["rules"]:
    names.update(rule["tasks"])

# Structural coverage check. A path that matches no rule falls through to
# unknown=full, and because full_tasks is a single shard that escalation is
# indistinguishable in the selector's output -- so prove it from the tree instead:
# every top-level module must be claimed by a rule, by full_paths, or be deliberately
# ignored. Without this, dropping magisk-xposed-kit/* from a rule looks like success.
import fnmatch
import os

claimed = list(data["full_paths"]) + list(data["ignore_paths"])
for rule in data["rules"]:
    claimed.extend(rule["paths"])
unclaimed = []
for name in sorted(os.listdir(root_dir)):
    path = os.path.join(root_dir, name)
    # Hidden directories are repository tooling (.github composite actions, .gitlab
    # templates, agent config), not Gradle modules: they are covered by ignore_paths and
    # must not be demanded as rule targets.
    if not os.path.isdir(path) or name.startswith(".") or name in ("build",):
        continue
    probe = name + "/probe.kt"
    if not any(fnmatch.fnmatchcase(probe, pattern) for pattern in claimed):
        unclaimed.append(name)
if unclaimed:
    raise SystemExit("top-level paths match no rule and would silently select everything: %s"
                     % ", ".join(unclaimed))

print("\n".join(sorted(names)))
PY
)"; then
  echo "android-test-tasks.json rejected (the reason is on the line above from python)" >&2
  exit 1
fi
mapfile -t referenced <<< "$referenced_raw"
if [[ ${#referenced[@]} -eq 0 || -z "${referenced[0]}" ]]; then
  echo "android-test-tasks.json selected no shards at all" >&2
  exit 1
fi

for shard in "${referenced[@]}"; do
  if ! is_android_test_shard "$shard"; then
    echo "config selects '$shard', which run_test_shards.sh does not implement" >&2
    exit 1
  fi
done

# Submodule churn and the manager/xmsfDelivery module must not fall through to
# unknown=full: that would quietly turn a narrow change into all three shards.
toolkit="${MAGISK_CI_TOOLKIT_DIR:-$root_dir/.magisk-ci-toolkit}"
selector="$toolkit/ci/select_gradle_tasks.py"
if [[ -f "$selector" ]]; then
  tmp_dir="$(mktemp -d)"; trap 'rm -rf "$tmp_dir"' EXIT
  for path in magisk-xposed-kit/diagnostics/src/main/java/A.kt \
              magisk-ui-kit/src/main/java/B.kt \
              manager/xmsfDelivery/src/main/java/C.kt; do
    printf 'impact\n%s\n' "$path" > "$tmp_dir/paths.txt"
    selected="$(
      env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
        python3 "$selector" --root "$root_dir" --toolkit "$toolkit" \
        --config "$config" --paths-file "$tmp_dir/paths.txt"
    )"
    if [[ "$selected" != "android-pure-modules" ]]; then
      echo "$path selected '$selected', expected exactly 'android-pure-modules'" >&2
      exit 1
    fi
  done
  # Documentation and workflow edits must select nothing at all.
  printf 'impact\ndocs/a.md\n.github/workflows/ci.yml\n' > "$tmp_dir/paths.txt"
  selected="$(
    env -u CI_COMMIT_TAG -u CI_COMMIT_BRANCH -u GITHUB_REF_TYPE -u GITHUB_REF_NAME \
      python3 "$selector" --root "$root_dir" --toolkit "$toolkit" \
      --config "$config" --paths-file "$tmp_dir/paths.txt"
  )"
  if [[ -n "$selected" ]]; then
    echo "docs/workflow-only change selected '$selected', expected no shards" >&2
    exit 1
  fi
else
  echo "note: toolkit selector absent at $selector, checked the config only" >&2
fi

echo "android-test-tasks config pins the shard names and the narrowing rules"
