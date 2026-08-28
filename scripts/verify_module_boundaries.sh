#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASELINE="scripts/module_boundary_baseline.txt"
VENDOR_BASELINE="scripts/vendor_boundary_baseline.txt"
COMMON_MAIN_JVM_COMPAT_BASELINE="scripts/common_main_jvm_compat_baseline.txt"
if [ ! -f "$BASELINE" ]; then
  echo "Missing module boundary baseline: $BASELINE" >&2
  exit 1
fi
if [ ! -f "$VENDOR_BASELINE" ]; then
  echo "Missing vendor boundary baseline: $VENDOR_BASELINE" >&2
  exit 1
fi
if [ ! -f "$COMMON_MAIN_JVM_COMPAT_BASELINE" ]; then
  echo "Missing commonMain JVM compatibility baseline: $COMMON_MAIN_JVM_COMPAT_BASELINE" >&2
  exit 1
fi

tmp_current="$(mktemp)"
tmp_baseline="$(mktemp)"
tmp_new="$(mktemp)"
tmp_stale="$(mktemp)"
tmp_forbidden_deps="$(mktemp)"
tmp_forbidden_xmsf_edges="$(mktemp)"
tmp_vendor_current="$(mktemp)"
tmp_vendor_baseline="$(mktemp)"
tmp_vendor_new="$(mktemp)"
tmp_vendor_stale="$(mktemp)"
tmp_common_main_jvm_current="$(mktemp)"
tmp_common_main_jvm_baseline="$(mktemp)"
tmp_common_main_jvm_new="$(mktemp)"
tmp_common_main_jvm_stale="$(mktemp)"
trap 'rm -f "$tmp_current" "$tmp_baseline" "$tmp_new" "$tmp_stale" "$tmp_forbidden_deps" "$tmp_forbidden_xmsf_edges" "$tmp_vendor_current" "$tmp_vendor_baseline" "$tmp_vendor_new" "$tmp_vendor_stale" "$tmp_common_main_jvm_current" "$tmp_common_main_jvm_baseline" "$tmp_common_main_jvm_new" "$tmp_common_main_jvm_stale"' EXIT

required_deep_xiaomi_scan_roots=(
  "manager/contract/src/main/aidl"
  "manager/contract/src/main/java"
  "manager/application/src/main/java"
  "manager/client/src/main/java"
  "manager/ui/src/main/java"
  "settings/src/main/java"
  "xmsf/shell/src/main/java/io/github/magisk317/mipush/app"
)

required_manager_app_scan_roots=(
  "manager/contract/src/main/aidl"
  "manager/contract/src/main/java"
  "manager/application/src/main/java"
  "manager/client/src/main/java"
  "manager/ui/src/main/java"
  "settings/src/main/java"
)

deep_xiaomi_pattern='^import com\.xiaomi\.(channel|mipush|network|push|smack|slim|stats|tinyData|xmpush)'
deep_xiaomi_fqcn_pattern='com\.xiaomi\.(channel|mipush|network|push|smack|slim|stats|tinyData|xmpush)\.[A-Za-z_]'
deep_xiaomi_class_string_pattern='"com\.xiaomi\.(channel|mipush|network|push|smack|slim|stats|tinyData|xmpush)(\.[A-Za-z_][A-Za-z0-9_]*)*\.[A-Z][A-Za-z0-9_]*[a-z][A-Za-z0-9_]*"'
manager_app_pattern='^import io\.github\.magisk317\.mipush\.app\.'
deep_xiaomi_string_pattern='"com\.xiaomi\.(channel|mipush|network|push|smack|slim|stats|tinyData|xmpush)'

require_scan_roots() {
  local root
  for root in "$@"; do
    if [ ! -d "$root" ]; then
      echo "Configured module-boundary scan root is missing: $root" >&2
      echo "Update scripts/verify_module_boundaries.sh when module source roots move." >&2
      exit 1
    fi
  done
}

require_scan_roots "${required_deep_xiaomi_scan_roots[@]}"
require_scan_roots "${required_manager_app_scan_roots[@]}"

{
  for root in "${required_deep_xiaomi_scan_roots[@]}"; do
    rg -n "$deep_xiaomi_pattern" "$root" || true
  done

  for root in "${required_deep_xiaomi_scan_roots[@]}"; do
    rg -n "$deep_xiaomi_fqcn_pattern" "$root" | awk '$0 !~ /"/ { print }' || true
  done

  for root in "${required_deep_xiaomi_scan_roots[@]}"; do
    rg -n "$deep_xiaomi_class_string_pattern" "$root" || true
  done

  for root in "${required_manager_app_scan_roots[@]}"; do
    rg -n "$manager_app_pattern" "$root" || true
  done

  rg -n "$deep_xiaomi_string_pattern" "manager/ui/src/main/java" "settings/src/main/java" || true
} | while IFS=: read -r path _line import_line; do
  [ -n "${path:-}" ] || continue
  printf '%s|%s\n' "$path" "$import_line"
done | sort -u > "$tmp_current"

sed -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$BASELINE" | sort -u > "$tmp_baseline"

comm -13 "$tmp_baseline" "$tmp_current" > "$tmp_new"
comm -23 "$tmp_baseline" "$tmp_current" > "$tmp_stale"

if [ -s "$tmp_new" ]; then
  echo "New architecture-boundary imports/references were added outside the allowed adapter areas." >&2
  echo "Move the dependency behind an xmsf runtime/bridge adapter, manager gateway, or explicit shared contract." >&2
  echo "Only update the baseline for deliberate, documented compatibility debt." >&2
  echo >&2
  cat "$tmp_new" >&2
  exit 1
fi

if [ -s "$tmp_stale" ]; then
  echo "Module boundary baseline contains stale entries." >&2
  echo "Remove entries that no longer appear in the scanned source roots." >&2
  echo >&2
  cat "$tmp_stale" >&2
  exit 1
fi

# vendor is retained stock/runtime code. Existing product imports are recorded as migration debt,
# but new imports must be routed through xmsf adapters instead of growing product behavior in the
# vendored source tree.
vendor_product_import_pattern='^import io\.github\.magisk317\.'
rg -n "$vendor_product_import_pattern" "vendor/src/main" \
  | while IFS=: read -r path _line import_line; do
    [ -n "${path:-}" ] || continue
    printf '%s|%s\n' "$path" "$import_line"
  done | sort -u > "$tmp_vendor_current" || true

sed -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$VENDOR_BASELINE" | sort -u > "$tmp_vendor_baseline"
comm -13 "$tmp_vendor_baseline" "$tmp_vendor_current" > "$tmp_vendor_new"
comm -23 "$tmp_vendor_baseline" "$tmp_vendor_current" > "$tmp_vendor_stale"

if [ -s "$tmp_vendor_new" ]; then
  echo "Vendor boundary check failed: new product-layer imports were added under vendor/." >&2
  echo "Move the behavior behind an xmsf runtime/bridge adapter, or document an explicit compatibility exception." >&2
  echo >&2
  cat "$tmp_vendor_new" >&2
  exit 1
fi

if [ -s "$tmp_vendor_stale" ]; then
  echo "Vendor boundary baseline contains stale entries." >&2
  echo "Remove entries that no longer appear under vendor/src/main." >&2
  echo >&2
  cat "$tmp_vendor_stale" >&2
  exit 1
fi

for build_file in "manager/ui/build.gradle.kts" "settings/build.gradle.kts"; do
  if [ -f "$build_file" ]; then
    rg -n 'project\(":(vendor|xmsf:shell|pinned)"\)' "$build_file" \
      | while IFS=: read -r path _line import_line; do
        [ -n "${path:-}" ] || continue
        printf '%s|%s\n' "$path" "$import_line"
      done >> "$tmp_forbidden_deps" || true
  fi
done

for build_file in "manager/contract/build.gradle.kts" "manager/client/build.gradle.kts"; do
  if [ -f "$build_file" ]; then
    rg -n 'project\(":(common|core|pinned|settings|vendor|xmsf:shell)"\)' "$build_file" \
      | while IFS=: read -r path _line import_line; do
        [ -n "${path:-}" ] || continue
        printf '%s|%s\n' "$path" "$import_line"
      done >> "$tmp_forbidden_deps" || true
  fi
done

if [ -f "manager/application/build.gradle.kts" ]; then
  rg -n 'project\(":(common|pinned|settings|vendor|xmsf(:[^\"]+)?)"\)' "manager/application/build.gradle.kts" \
    | while IFS=: read -r path _line import_line; do
      [ -n "${path:-}" ] || continue
      printf '%s|%s\n' "$path" "$import_line"
    done >> "$tmp_forbidden_deps" || true
fi

# Freeze the reviewed project-dependency DAG for foundational and runtime feature modules.
# The shell/app modules are composition roots and intentionally are not constrained here.
# vendor is frozen compatibility code: its current store edge is recorded as debt so no new
# lower-layer edge can be introduced; remove the edge only with an explicit vendor review.
python3 - "$tmp_forbidden_deps" <<'PY'
from pathlib import Path
import re
import sys

output = Path(sys.argv[1])
allowed = {
    "core/build.gradle.kts": set(),
    "common/build.gradle.kts": {":core", ":diagnostics"},
    "configuration/build.gradle.kts": {":core", ":common", ":settings"},
    "diagnostics/build.gradle.kts": {":magisk-xposed-kit:logging", ":magisk-xposed-kit:diagnostics"},
    "settings/build.gradle.kts": {":common", ":core"},
    "xposed/build.gradle.kts": {":common", ":magisk-xposed-kit"},
    "vendor/build.gradle.kts": {
        ":common", ":core", ":pinned", ":xmsf:runtime:store", ":magisk-xposed-kit:logging",
    },
    "xmsf/platform/build.gradle.kts": {":common"},
    "xmsf/runtime/store/build.gradle.kts": {":core"},
    "manager/contract/build.gradle.kts": set(),
    "manager/application/build.gradle.kts": {":core"},
    "manager/client/build.gradle.kts": {":manager:contract", ":magisk-xposed-kit:logging"},
    "xmsf/runtime/build.gradle.kts": {
        ":common", ":manager:application", ":core", ":pinned", ":vendor",
        ":xmsf:runtime:store", ":magisk-xposed-kit:logging",
    },
    "xmsf/push/build.gradle.kts": {
        ":common", ":core", ":pinned", ":vendor", ":xmsf:runtime",
        ":xmsf:runtime:store", ":magisk-xposed-kit:logging",
    },
    "xmsf/notification/build.gradle.kts": {
        ":common", ":core", ":xmsf:platform", ":pinned", ":vendor", ":settings",
        ":xmsf:runtime", ":xmsf:runtime:store", ":magisk-xposed-kit:logging",
    },
}
violations = []
pattern = re.compile(r'project\("(:[^"]+)"\)')
for path_text, permitted in allowed.items():
    path = Path(path_text)
    if not path.is_file():
        violations.append(f"{path_text}|missing reviewed DAG build file")
        continue
    actual = set(pattern.findall(path.read_text(encoding="utf-8")))
    for dependency in sorted(actual - permitted):
        violations.append(f"{path_text}|unexpected project dependency {dependency}")

if violations:
    with output.open("a", encoding="utf-8") as stream:
        stream.write("\n".join(violations) + "\n")
PY

if [ -s "$tmp_forbidden_deps" ]; then
  echo "Project dependency DAG check failed: reviewed modules contain forbidden project dependencies." >&2
  echo "Keep manager contracts independent of runtime implementations and do not add lower-layer edges." >&2
  echo >&2
  cat "$tmp_forbidden_deps" >&2
  exit 1
fi

# KMP source-set dependencies are a separate boundary from the project DAG: a dependency that is
# valid for androidMain must not silently become available to portable commonMain. Keep this exact
# allowlist deliberately small; update it only with a source-set architecture review.
kmp_source_set_dependency_violations="$(python3 - <<'PYTHON'
from pathlib import Path
import re

expected = {
    "core/build.gradle.kts": {
        "commonMain": {
            "implementation(libs.kermit)",
            "implementation(libs.kotlinx.serialization.json)",
        },
    },
    "configuration/build.gradle.kts": {
        "commonMain": {'implementation(project(":core"))'},
        "androidMain": {
            'implementation(project(":common"))',
            'implementation(project(":settings"))',
            "implementation(libs.kotlinx.serialization.json)",
            "implementation(libs.androidx.documentfile)",
        },
    },
    "xmsf/platform/build.gradle.kts": {
        "androidMain": {
            'implementation(project(":common"))',
            "implementation(libs.libsu.core)",
            "implementation(libs.palette)",
        },
    },
    "xmsf/runtime/store/build.gradle.kts": {
        "commonMain": {
            'implementation(project(":core"))',
            "implementation(libs.androidx.room.runtime)",
            "implementation(libs.androidx.sqlite.bundled)",
            "implementation(libs.kermit)",
        },
    },
}
block_pattern = re.compile(r"(\w+)\.dependencies\s*\{(?P<body>[^}]*)\}", re.DOTALL)
dependency_pattern = re.compile(r"^\s*(?:api|implementation)\((.+)\)\s*$", re.MULTILINE)
violations = []
for path_text, expected_sets in expected.items():
    path = Path(path_text)
    if not path.is_file():
        violations.append(f"{path_text}|missing KMP build file")
        continue
    actual_sets = {}
    for match in block_pattern.finditer(path.read_text(encoding="utf-8")):
        dependencies = {
            f"implementation({value.strip()})"
            for value in dependency_pattern.findall(match.group("body"))
        }
        actual_sets[match.group(1)] = dependencies
    for source_set in sorted(set(actual_sets) | set(expected_sets)):
        actual = actual_sets.get(source_set, set())
        allowed = expected_sets.get(source_set, set())
        for dependency in sorted(actual - allowed):
            violations.append(f"{path_text}|{source_set}|unexpected dependency {dependency}")
        for dependency in sorted(allowed - actual):
            violations.append(f"{path_text}|{source_set}|missing reviewed dependency {dependency}")

print("\n".join(violations))
PYTHON
)"
if [ -n "$kmp_source_set_dependency_violations" ]; then
  echo "KMP source-set dependency check failed." >&2
  echo "Keep portable commonMain dependencies separate from Android-only source-set dependencies." >&2
  echo >&2
  printf '%s\n' "$kmp_source_set_dependency_violations" >&2
  exit 1
fi

# Remove comments and string literals before checking fully-qualified source references. This
# catches Android/JVM types that bypass imports without treating opaque compatibility strings as APIs.
find_code_platform_references() {
  local pattern="$1"
  shift
  python3 - "$pattern" "$@" <<'PY'
from pathlib import Path
import re
import sys

pattern = re.compile(sys.argv[1])
for root_arg in sys.argv[2:]:
    for path in Path(root_arg).rglob("*.kt"):
        in_block_comment = False
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            code = ""
            index = 0
            while index < len(line):
                if in_block_comment:
                    close = line.find("*/", index)
                    if close < 0:
                        index = len(line)
                        continue
                    in_block_comment = False
                    index = close + 2
                    continue
                if line.startswith("/*", index):
                    in_block_comment = True
                    index += 2
                    continue
                if line.startswith("//", index):
                    break
                if line[index] == '"':
                    index += 1
                    while index < len(line):
                        if line[index] == "\\":
                            index += 2
                        elif line[index] == '"':
                            index += 1
                            break
                        else:
                            index += 1
                    continue
                code += line[index]
                index += 1
            if pattern.search(code):
                print(f"{path}:{line_number}:{code.strip()}")
PY
}

# common is a shared contract/model layer. Protocol serialization and Thrift
# types belong to the xmsf runtime adapter and must not leak back into it.
common_protocol_leaks="$(
  rg -n 'com\.xiaomi\.xmpush\.thrift|org\.apache\.thrift|project\(":pinned"\)' \
    "common/src" "common/build.gradle.kts" || true
)"
if [ -n "$common_protocol_leaks" ]; then
  echo "common must not depend on pinned or expose Thrift protocol types." >&2
  echo >&2
  printf '%s\n' "$common_protocol_leaks" >&2
  exit 1
fi

# commonMain may retain only explicitly reviewed JVM compatibility debt. Count by path and
# kind so adding or removing a direct platform call/@JvmStatic requires a baseline review rather
# than silently widening the portable surface.
collect_common_main_jvm_compatibility_debt() {
  python3 - "$@" <<'PYTHON'
from collections import Counter
from pathlib import Path
import re
import sys

implicit_jvm = re.compile(r"\b(?:System|Runtime|Thread)\.")
jvm_static = re.compile(r"@JvmStatic\b")
entries = Counter()
for root_arg in sys.argv[1:]:
    root = Path(root_arg)
    for path in root.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        implicit_count = len(implicit_jvm.findall(text))
        static_count = len(jvm_static.findall(text))
        relative = path.as_posix()
        if implicit_count:
            entries[(relative, "implicitJvmApi")] = implicit_count
        if static_count:
            entries[(relative, "jvmStatic")] = static_count
for (path, kind), count in sorted(entries.items()):
    print(f"{path}|{kind}|{count}")
PYTHON
}

common_main_jvm_compat_roots=(
  "core/src/commonMain"
  "configuration/src/commonMain"
  "xmsf/platform/src/commonMain"
  "xmsf/runtime/store/src/commonMain"
)
require_scan_roots "${common_main_jvm_compat_roots[@]}"
collect_common_main_jvm_compatibility_debt "${common_main_jvm_compat_roots[@]}"   | sort -u > "$tmp_common_main_jvm_current"
sed -e '/^[[:space:]]*#/d' -e '/^[[:space:]]*$/d' "$COMMON_MAIN_JVM_COMPAT_BASELINE"   | sort -u > "$tmp_common_main_jvm_baseline"
comm -13 "$tmp_common_main_jvm_baseline" "$tmp_common_main_jvm_current" > "$tmp_common_main_jvm_new"
comm -23 "$tmp_common_main_jvm_baseline" "$tmp_common_main_jvm_current" > "$tmp_common_main_jvm_stale"
if [ -s "$tmp_common_main_jvm_new" ] || [ -s "$tmp_common_main_jvm_stale" ]; then
  echo "commonMain JVM compatibility boundary changed." >&2
  echo "Move platform behavior behind an adapter, or explicitly review the exact compatibility debt." >&2
  if [ -s "$tmp_common_main_jvm_new" ]; then
    echo "New or increased compatibility debt:" >&2
    cat "$tmp_common_main_jvm_new" >&2
  fi
  if [ -s "$tmp_common_main_jvm_stale" ]; then
    echo "Stale compatibility baseline entries:" >&2
    cat "$tmp_common_main_jvm_stale" >&2
  fi
  exit 1
fi

# core commonMain is the portable policy boundary. Android APIs, Xiaomi/Thrift
# protocol types, and JVM APIs belong in platform/runtime adapters rather than here.
require_scan_roots "core/src/commonMain"
core_common_main_leaks="$(find_code_platform_references \
  '\b(android\.|java\.|javax\.|org\.apache\.thrift\.)' \
  "core/src/commonMain")"
if [ -n "$core_common_main_leaks" ]; then
  echo "core commonMain must remain free of Android, JVM, and Thrift platform API references." >&2
  echo >&2
  printf '%s\n' "$core_common_main_leaks" >&2
  exit 1
fi

# configuration/platform own portable contracts. Android/JVM APIs, AndroidX UI/storage APIs,
# Xiaomi protocol types, and libsu belong to androidMain adapters.
portable_common_roots=(
  "configuration/src/commonMain"
  "xmsf/platform/src/commonMain"
)
require_scan_roots "${portable_common_roots[@]}"
portable_common_leaks="$(find_code_platform_references \
  '\b(android\.|androidx\.|java\.|javax\.|com\.xiaomi\.|com\.topjohnwu\.|org\.apache\.thrift\.)' \
  "${portable_common_roots[@]}")"
if [ -n "$portable_common_leaks" ]; then
  echo "configuration/platform commonMain must remain platform-neutral." >&2
  echo "Move Android, JVM I/O, Xiaomi protocol, or libsu behavior to androidMain adapters." >&2
  echo >&2
  printf '%s\n' "$portable_common_leaks" >&2
  exit 1
fi

# runtime/store is deliberately Android-only KMP today: schema/DAO/policy code is portable,
# while its Context-backed Room builder is in androidMain. Keep platform/protocol APIs out of
# commonMain without banning Room/SQLite KMP types.
runtime_store_common_root="xmsf/runtime/store/src/commonMain"
require_scan_roots "$runtime_store_common_root"
runtime_store_common_leaks="$(find_code_platform_references \
  '\b(android\.|java\.(io|net)\.|javax\.|com\.xiaomi\.|com\.topjohnwu\.|org\.apache\.thrift\.)' \
  "$runtime_store_common_root")"
if [ -n "$runtime_store_common_leaks" ]; then
  echo "runtime/store commonMain must remain free of Android, JVM I/O, Xiaomi/Thrift, and libsu references." >&2
  echo "Keep the Room database builder and Android services in androidMain adapters." >&2
  echo >&2
  printf '%s\n' "$runtime_store_common_leaks" >&2
  exit 1
fi

misplaced_portable_contracts="$({
  rg -n '^(data class ConfigListSnapshot|fun interface ConfigSyncObserver)\b' \
    "configuration/src/androidMain" || true
  rg -n '^(enum class ShellCommandMode|data class BoundedShellResult|interface BoundedShellRunner)\b' \
    "xmsf/platform/src/androidMain" || true
})"
if [ -n "$misplaced_portable_contracts" ]; then
  echo "Portable configuration/platform contracts were moved back into androidMain." >&2
  echo >&2
  printf '%s\n' "$misplaced_portable_contracts" >&2
  exit 1
fi

required_portable_contracts=(
  "configuration/src/commonMain/kotlin/io/github/magisk317/mipush/configuration/ConfigContracts.kt"
  "xmsf/platform/src/commonMain/kotlin/io/github/magisk317/mipush/platform/support/BoundedShellContract.kt"
)
for contract_path in "${required_portable_contracts[@]}"; do
  if [ ! -f "$contract_path" ]; then
    echo "Required portable contract source is missing: $contract_path" >&2
    echo "Update the source-set boundary verifier deliberately if this contract moves." >&2
    exit 1
  fi
done

# Runtime data may consume :core notification.policy rules, but must never call the
# Android notification feature implementation directly.
xmsf_layer_edges=(
  "xmsf/shell/src/main/java/io/github/magisk317/mipush/runtime/data|io.github.magisk317.mipush.notification."
)
for edge in "${xmsf_layer_edges[@]}"; do
  source_root="${edge%%|*}"
  forbidden_import="${edge#*|}"
  rg -n "^import ${forbidden_import}" "$source_root" \
    | rg -v 'import io\.github\.magisk317\.mipush\.notification\.policy\.' \
    | while IFS=: read -r path _line import_line; do
      [ -n "${path:-}" ] || continue
      printf '%s|%s\n' "$path" "$import_line"
    done >> "$tmp_forbidden_xmsf_edges" || true
done

if [ -s "$tmp_forbidden_xmsf_edges" ]; then
  echo "xmsf package layering check failed: runtime data reaches notification implementation." >&2
  echo "Expose a common contract and bind the platform adapter in xmsf Koin instead." >&2
  echo >&2
  cat "$tmp_forbidden_xmsf_edges" >&2
  exit 1
fi

# Manager IPC is owned by :manager:contract. The runtime implements the generated Stub and
# the client consumes only that contract; no other manager module may define AIDL or wire API
# package types. Stock extension AIDL remains separately owned by xmsf/shell.
manager_ipc_ownership_violations="$(python3 - <<'PYTHON'
from pathlib import Path

contract_aidl_root = Path("manager/contract/src/main/aidl")
contract_root = Path("manager/contract")
violations = []
for path in Path("manager").rglob("*.aidl"):
    if "build" in path.parts:
        continue
    if not path.is_relative_to(contract_aidl_root):
        violations.append(f"{path}|manager AIDL must be owned by manager/contract")
for path in Path(".").rglob("*.kt"):
    if ".git" in path.parts or any(part in {"build", "vendor", "pinned", "magisk-ui-kit", "magisk-xposed-kit"} for part in path.parts):
        continue
    text = path.read_text(encoding="utf-8")
    if text.startswith("package io.github.magisk317.mipush.manager.api") and not path.is_relative_to(contract_root):
        violations.append(f"{path}|manager.api wire package must be owned by manager/contract")
print("\n".join(sorted(violations)))
PYTHON
)"
if [ -n "$manager_ipc_ownership_violations" ]; then
  echo "Manager IPC ownership check failed." >&2
  echo "Keep Manager AIDL and manager.api wire DTO/framing types in :manager:contract." >&2
  echo >&2
  printf '%s\n' "$manager_ipc_ownership_violations" >&2
  exit 1
fi

manager_ipc_contract_violations="$({
  if ! rg -q 'aidl = true' "manager/contract/build.gradle.kts"; then
    echo "manager/contract/build.gradle.kts|manager contract must enable AIDL"
  fi
  if ! rg -q 'api\(project\(":manager:contract"\)\)' "manager/client/build.gradle.kts"; then
    echo "manager/client/build.gradle.kts|manager client must expose manager contract as API"
  fi
  if ! rg -q 'class ManagerRuntimeService : Service' \
      "xmsf/shell/src/main/java/io/github/magisk317/mipush/manager/runtime/ManagerRuntimeService.kt"; then
    echo "ManagerRuntimeService.kt|runtime service implementation is missing"
  fi
  if ! rg -q 'IManagerRuntimeService\.Stub' \
      "xmsf/shell/src/main/java/io/github/magisk317/mipush/manager/runtime/ManagerRuntimeService.kt"; then
    echo "ManagerRuntimeService.kt|runtime service must implement the contract Stub"
  fi
})"
if [ -n "$manager_ipc_contract_violations" ]; then
  echo "Manager IPC contract-consumer check failed." >&2
  echo >&2
  printf '%s\n' "$manager_ipc_contract_violations" >&2
  exit 1
fi

# Manager application ports may use Android host context/URI where required, but archive
# files and share intents are UI-host responsibilities. Keep those framework types out of
# the application API surface so Binder-independent contracts stay testable and portable.
manager_application_framework_leaks="$({
  rg -n '^(import (java\.io\.File|android\.content\.Intent)|.*\b(java\.io\.File|android\.content\.Intent)\b)' \
    "manager/application/src/main" || true
})"
if [ -n "$manager_application_framework_leaks" ]; then
  echo "Manager application contracts must not expose File or Intent; return path/value results and let the UI host adapt them." >&2
  echo >&2
  printf '%s\n' "$manager_application_framework_leaks" >&2
  exit 1
fi

# Runtime_Boundary for the navigation-performance and modernization work: all working-tree
# changes must remain in explicitly reviewed module/config surfaces or this verifier. This
# intentionally includes untracked files so a new implementation cannot bypass the check
# before it is staged. The isolated Room/KMP shadow module and its minimal root wiring are
# deliberate additions to the reviewed surface.
tmp_changed_paths="$(mktemp)"
tmp_boundary_violations="$(mktemp)"
tmp_uikit_violations="$(mktemp)"
tmp_aidl_changes="$(mktemp)"
trap 'rm -f "$tmp_current" "$tmp_baseline" "$tmp_new" "$tmp_stale" "$tmp_forbidden_deps" "$tmp_forbidden_xmsf_edges" "$tmp_vendor_current" "$tmp_vendor_baseline" "$tmp_vendor_new" "$tmp_vendor_stale" "$tmp_changed_paths" "$tmp_boundary_violations" "$tmp_uikit_violations" "$tmp_aidl_changes"' EXIT

{
  git diff --name-only --diff-filter=ACMRT HEAD
  git ls-files --others --exclude-standard
} | sort -u > "$tmp_changed_paths"

while IFS= read -r changed_path; do
  [ -n "$changed_path" ] || continue
  case "$changed_path" in
    magisk-ui-kit|magisk-ui-kit/*|magisk-xposed-kit|magisk-xposed-kit/*|manager/ui/*|manager/client/*|manager/contract/*|manager/application/*|settings|settings/*|xposed/*|core|core/*|xmsf/*|vendor/*|common|common/*|configuration|configuration/*|mipush|mipush/*|app/build.gradle.kts|mipush/build.gradle.kts|README.md|.github/workflows/ci.yml|.gitlab-ci.yml|scripts/checks/verify_shared_submodule_compat.sh|scripts/checks/verify_ci_toolkit_ref.sh|scripts/checks/report_god_files.sh|scripts/god_file_limits.txt|scripts/ci/*|scripts/release_tag.sh|scripts/verify_module_boundaries.sh|scripts/vendor_boundary_baseline.txt|scripts/common_main_jvm_compat_baseline.txt|xmsf/runtime/store/*|build.gradle.kts|settings.gradle.kts|gradle/libs.versions.toml|docs|docs/*)
      ;;
    *)
      printf '%s\n' "$changed_path" >> "$tmp_boundary_violations"
      ;;
  esac
done < "$tmp_changed_paths"

# AIDL files are in the allowed manager contract module, but their external semantics
# are explicitly outside this feature's boundary. Any working-tree AIDL change
# therefore fails until it is reviewed as a separate protocol change.
while IFS= read -r changed_path; do
  case "$changed_path" in
    manager/contract/src/main/aidl/*|xmsf/shell/src/main/aidl/*)
      printf '%s\n' "$changed_path" >> "$tmp_aidl_changes"
      ;;
    */src/main/aidl/*|*.aidl)
      printf '%s\n' "$changed_path" >> "$tmp_aidl_changes"
      ;;
  esac
done < "$tmp_changed_paths"

if [ -s "$tmp_boundary_violations" ]; then
  echo "Runtime boundary check failed: changed files are outside the allowed modules." >&2
  echo "Allowed: UI/manager modules, xposed, xmsf/vendor warning fixes, and this verifier." >&2
  cat "$tmp_boundary_violations" >&2
  exit 1
fi

if [ -s "$tmp_aidl_changes" ]; then
  echo "Runtime boundary check failed: AIDL files changed; external Binder semantics must remain unchanged." >&2
  cat "$tmp_aidl_changes" >&2
  exit 1
fi

# UI Kit is project-neutral. Check both source and its Gradle dependencies so a
# MiPush route/model/client cannot be introduced through either an import or a
# module edge. The manager is the only owner of these contracts.
{
  rg -n 'io\.github\.magisk317\.mipush|com\.xiaomi\.xmsf' \
    magisk-ui-kit/src magisk-ui-kit/build.gradle.kts || true
  rg -n 'project\(":(manager:ui|manager:contract|manager:client|xmsf:shell|vendor|pinned)"\)' \
    magisk-ui-kit/build.gradle.kts || true
} > "$tmp_uikit_violations"

if [ -s "$tmp_uikit_violations" ]; then
  echo "UI Kit boundary check failed: MiPush-specific contracts or runtime dependencies were found." >&2
  cat "$tmp_uikit_violations" >&2
  exit 1
fi

manager_notification_framework="$({
  rg -n 'android\.app\.NotificationChannel(Group)?' "manager/ui/src/main/java" || true
})"
if [ -n "$manager_notification_framework" ]; then
  echo "Manager notification UI must consume DTO/domain models, not Android notification channels." >&2
  echo >&2
  printf '%s\n' "$manager_notification_framework" >&2
  exit 1
fi

app_host="app/src/main/java/com/xiaomi/xmsf/app/MiPushHostApp.kt"
mipush_host="mipush/src/main/java/io/github/magisk317/mipush/app/App.kt"
framework_host="xmsf/shell/src/main/java/io/github/magisk317/mipush/app/MiPushFrameworkApp.kt"

require_bootstrap_contract() {
  local pattern="$1"
  local file="$2"
  local message="$3"
  if ! rg -q "$pattern" "$file"; then
    echo "Manager bootstrap contract violation: $message" >&2
    exit 1
  fi
}

require_bootstrap_contract \
  'implementation\(project\(":manager:ui"\)\)' \
  "app/build.gradle.kts" \
  ":app must package the manager bootstrap implementation."
require_bootstrap_contract \
  'override fun onAppDependenciesStarted\(\)' \
  "$app_host" \
  "MiPushHostApp must use the post-AppDependencies startup hook."
require_bootstrap_contract \
  'PushControllerUtils\.isAppMainProc\(this\)' \
  "$app_host" \
  "MiPushHostApp must gate manager startup to the main process."
require_bootstrap_contract \
  'ManagerDependencies\.startFromAppShell\(this\)' \
  "$app_host" \
  "MiPushHostApp must own app-shell manager startup."
require_bootstrap_contract \
  'protected open fun onAppDependenciesStarted\(\)' \
  "$framework_host" \
  "MiPushFrameworkApp must expose the post-AppDependencies host hook."
require_bootstrap_contract \
  'ManagerDependencies\.startAsRemoteHost\(this, billingModule\)' \
  "$mipush_host" \
  ":mipush Application must own standalone remote-host startup."

forbidden_entrypoint_bootstrap="$({
  rg -n 'ManagerDependencies\.(ensureStarted|startAsRemoteHost|startFromAppShell)' \
    "manager/ui/src/main/java/io/github/magisk317/mipush/feature" \
    "mipush/src/main/java/io/github/magisk317/mipush/app/ManagerLauncherActivity.kt" \
    "mipush/src/main/java/io/github/magisk317/mipush/app/widget" || true
})"
if [ -n "$forbidden_entrypoint_bootstrap" ]; then
  echo "Manager UI, launcher, and widget entrypoints must consume Application-owned bootstrap." >&2
  echo >&2
  printf '%s\n' "$forbidden_entrypoint_bootstrap" >&2
  exit 1
fi

xmsf_manager_bootstrap="$({
  rg -n 'io\.github\.magisk317\.mipush\.manager\.di|managerKoinModule|ManagerDependencies' \
    "xmsf/shell/src/main/java" || true
})"
if [ -n "$xmsf_manager_bootstrap" ]; then
  echo "xmsf Koin/runtime sources must not own manager UI bootstrap definitions." >&2
  echo >&2
  printf '%s\n' "$xmsf_manager_bootstrap" >&2
  exit 1
fi

echo "Module boundary check passed."
