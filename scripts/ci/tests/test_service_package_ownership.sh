#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"

# Exercise the exact checker embedded in the normal boundary gate against isolated sources.
# Removing either ownership rule must let a misplaced production declaration through and fail here.
python3 - "$root_dir/scripts/checks/verify_module_boundaries.sh" <<'PY'
from pathlib import Path
import subprocess
import sys
import tempfile

source = Path(sys.argv[1]).read_text(encoding="utf-8")
start = "python3 - <<'SERVICE_PACKAGE_OWNERSHIP'\n"
end = "\nSERVICE_PACKAGE_OWNERSHIP"
assert start in source, "boundary gate is missing the service package ownership checker"
checker = source.split(start, 1)[1].split(end, 1)[0]


def check(files, expected_path=None):
    with tempfile.TemporaryDirectory(prefix="mipush-service-ownership-") as directory:
        root = Path(directory)
        for module in ("runtime", "shell"):
            (root / "xmsf" / module / "src/main").mkdir(parents=True)
        for name, text in files.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        result = subprocess.run(
            [sys.executable, "-"], input=checker, cwd=root,
            text=True, capture_output=True, check=False,
        )
        if expected_path is None:
            assert result.returncode == 0, result.stdout + result.stderr
        else:
            assert result.returncode != 0, f"checker accepted {expected_path}"
            assert expected_path in result.stdout + result.stderr, result.stdout + result.stderr


check({
    "xmsf/runtime/src/main/java/Runtime.kt":
        "/* package io.github.magisk317.mipush.service.runtime */\n"
        "package io.github.magisk317.mipush.runtime.service\nobject Runtime\n",
    "xmsf/shell/src/main/kotlin/Shell.kt":
        "package io.github.magisk317.mipush.service.runtime\nobject Shell\n",
    "xmsf/runtime/src/main/java/Unrelated.kt":
        "package io.github.magisk317.mipush.runtime.serviceextra\nobject Unrelated\n",
    "xmsf/runtime/src/test/java/Test.kt":
        "package io.github.magisk317.mipush.service.runtime\nclass Test\n",
})
for module, package, suffix in (
    ("runtime", "service.runtime", "kt"),
    ("shell", "runtime.service", "kt"),
    ("runtime", "service.runtime.child", "java"),
    ("shell", "runtime.service.child", "java"),
):
    path = f"xmsf/{module}/src/main/java/Misplaced.{suffix}"
    check({path: f"// compatibility adapter\npackage io.github.magisk317.mipush.{package};\n"}, path)
print("Service package ownership tests passed (valid owners and four misplaced declarations).")
PY
