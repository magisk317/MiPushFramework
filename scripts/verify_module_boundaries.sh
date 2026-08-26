#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASELINE="scripts/module_boundary_baseline.txt"
VENDOR_BASELINE="scripts/vendor_boundary_baseline.txt"
if [ ! -f "$BASELINE" ]; then
  echo "Missing module boundary baseline: $BASELINE" >&2
  exit 1
fi
if [ ! -f "$VENDOR_BASELINE" ]; then
  echo "Missing vendor boundary baseline: $VENDOR_BASELINE" >&2
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
trap 'rm -f "$tmp_current" "$tmp_baseline" "$tmp_new" "$tmp_stale" "$tmp_forbidden_deps" "$tmp_forbidden_xmsf_edges" "$tmp_vendor_current" "$tmp_vendor_baseline" "$tmp_vendor_new" "$tmp_vendor_stale"' EXIT

required_deep_xiaomi_scan_roots=(
  "manager/contract/src/main/aidl"
  "manager/contract/src/main/java"
  "manager/client/src/main/java"
  "manager/ui/src/main/java"
  "settings/src/main/java"
  "xmsf/shell/src/main/java/io/github/magisk317/mipush/app"
)

required_manager_app_scan_roots=(
  "manager/contract/src/main/aidl"
  "manager/contract/src/main/java"
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

if [ -s "$tmp_forbidden_deps" ]; then
  echo "Manager boundary build scripts contain forbidden project dependencies." >&2
  echo "Manager UI may use shared contracts; manager contract/client must stay independent of runtime implementations." >&2
  echo >&2
  cat "$tmp_forbidden_deps" >&2
    exit 1
fi

# common is a shared contract/model layer. Protocol serialization and Thrift
# types belong to the xmsf runtime adapter and must not leak back into it.
common_protocol_leaks="$({
  rg -n 'com\.xiaomi\.xmpush\.thrift|org\.apache\.thrift|project\(":pinned"\)' \
    "common/src" "common/build.gradle.kts" || true
})"
if [ -n "$common_protocol_leaks" ]; then
  echo "common must not depend on pinned or expose Thrift protocol types." >&2
  echo >&2
  printf '%s\n' "$common_protocol_leaks" >&2
  exit 1
fi

# Keep the first package-level xmsf layering rule mechanical. Runtime data owns
# persistence and replay orchestration; notification policy is supplied through
# a common contract and implemented by an xmsf adapter. This prevents a data
# repository from reaching back into Android notification construction APIs.
xmsf_layer_edges=(
  "xmsf/shell/src/main/java/io/github/magisk317/mipush/runtime/data|io.github.magisk317.mipush.notification."
)
for edge in "${xmsf_layer_edges[@]}"; do
  source_root="${edge%%|*}"
  forbidden_import="${edge#*|}"
  rg -n "^import ${forbidden_import}" "$source_root" \
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
    magisk-ui-kit|magisk-ui-kit/*|magisk-xposed-kit|magisk-xposed-kit/*|manager/ui/*|manager/client/*|manager/contract/*|settings|settings/*|xposed/*|core|core/*|xmsf/*|vendor/*|common|common/*|configuration|configuration/*|app/build.gradle.kts|mipush/build.gradle.kts|README.md|.github/workflows/ci.yml|.gitlab-ci.yml|scripts/checks/verify_shared_submodule_compat.sh|scripts/ci/*|scripts/release_tag.sh|scripts/verify_module_boundaries.sh|scripts/vendor_boundary_baseline.txt|xmsf/runtime/store/*|build.gradle.kts|settings.gradle.kts|gradle/libs.versions.toml|docs|docs/*)
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
      case "$changed_path" in
        manager/contract/*) old_path="manager-api/${changed_path#manager/contract/}" ;;
        xmsf/shell/*) old_path="xmsf/${changed_path#xmsf/shell/}" ;;
      esac
      if git cat-file -e "HEAD:$old_path" 2>/dev/null &&
        git show "HEAD:$old_path" | cmp -s - "$changed_path"; then
        continue
      fi
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
