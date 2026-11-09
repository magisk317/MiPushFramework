#!/usr/bin/env bash
# Static regression checks for GitLab CI behavior that shell tests cannot see.
# Pins blocking artifact staging, retained OTEL after_script, and the combined
# optional Detekt/God File Limits job.
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
ci_file="$root_dir/.gitlab-ci.yml"
[[ -f "$ci_file" ]] || { echo "missing $ci_file" >&2; exit 1; }

python3 - "$ci_file" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
lines = path.read_text(encoding="utf-8").splitlines()
top_level_key = re.compile(r"^[A-Za-z0-9_.:/-]+:\s*(?:#.*)?$")
direct_field = re.compile(r"^  ([A-Za-z0-9_.-]+):(?:\s.*)?$")


def fail(message):
    raise SystemExit(f"FAIL: {message}")


def get_job(name):
    header = f"{name}:"
    starts = [i for i, line in enumerate(lines) if line == header]
    if len(starts) != 1:
        fail(f"expected one top-level {header!r} job, found {len(starts)}")
    start = starts[0]
    end = len(lines)
    for i in range(start + 1, len(lines)):
        if top_level_key.fullmatch(lines[i]):
            end = i
            break
    return lines[start + 1:end]


def get_field(job, name):
    header = f"  {name}:"
    starts = [i for i, line in enumerate(job)
              if line.startswith(header) and (line == header or line[len(header)].isspace())]
    if len(starts) != 1:
        fail(f"expected one {name!r} field, found {len(starts)}")
    start = starts[0]
    end = len(job)
    for i in range(start + 1, len(job)):
        if direct_field.match(job[i]):
            end = i
            break
    return job[start + 1:end]


build = get_job("android:build")
script = get_field(build, "script")
after_script = get_field(build, "after_script")

reference = next((i for i, line in enumerate(script)
                 if line.strip() == "- !reference [.magisk_android_build, script]"), None)
verify = next((i for i, line in enumerate(script)
               if line.strip() == 'bash scripts/checks/verify_release_artifact.sh "$apk" || status=1'), None)
stage = next((i for i, line in enumerate(script)
              if line.strip() == "- bash scripts/ci/stage_ci_dist.sh"), None)
if reference is None:
    fail("android:build script no longer runs the inherited .magisk_android_build script")
if verify is None:
    fail("android:build script is missing its release-artifact verification")
if stage is None:
    fail("stage_ci_dist.sh must run as a blocking android:build script command")
if not reference < verify < stage:
    fail("staging must follow both the inherited build and artifact verification")
if any("stage_ci_dist.sh" in line and not line.lstrip().startswith("#")
       for line in after_script):
    fail("stage_ci_dist.sh must not run from after_script")
if not any("ci/otel_export.sh" in line and not line.lstrip().startswith("#")
           for line in after_script):
    fail("android:build after_script must retain the OTEL export")

# Keep Detekt + verifyGodFileLimits combined and optional, as approved.
detekt = get_job("android:detekt")
if not any(re.fullmatch(r"  allow_failure:\s*true\s*(?:#.*)?", line)
           for line in detekt):
    fail("android:detekt must keep allow_failure: true")
detekt_variables = get_field(detekt, "variables")
if not any(line.strip() == "MAGISK_ANDROID_DETEKT_TASKS: >-" for line in detekt_variables):
    fail("android:detekt must retain its combined MAGISK_ANDROID_DETEKT_TASKS list")
for task in ("detekt", "qualityGateDetekt", "verifyGodFileLimits"):
    if not any(line.strip() == task for line in detekt_variables):
        fail(f"android:detekt task list must include {task}")

print("OK: staging is blocking; OTEL and optional combined Detekt config are retained")
PY
