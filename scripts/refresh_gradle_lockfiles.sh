#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cleanup_submodule_lockfiles() {
  local submodule
  for submodule in uikit legacy pinned; do
    [ -e "${ROOT_DIR}/${submodule}/.git" ] || continue

    while IFS= read -r lockfile; do
      local relative_path="${lockfile#${ROOT_DIR}/${submodule}/}"
      if git -C "${ROOT_DIR}/${submodule}" ls-files --error-unmatch "${relative_path}" >/dev/null 2>&1; then
        continue
      fi
      rm -f "${lockfile}"
    done < <(find "${ROOT_DIR}/${submodule}" \( -name 'gradle.lockfile' -o -name 'settings-gradle.lockfile' \) -type f)
  done
}

cd "${ROOT_DIR}"

# Resolve the same task graph that CI relies on so dependency locks stay aligned
# with the configurations we actually exercise in automation.
bash "${ROOT_DIR}/scripts/with_workspace_gradle_lock.sh" \
  --write-locks \
  --warning-mode all \
  :core:check \
  :common:check \
  :xposed:check \
  :xmsf:check \
  :mipush:check \
  :xmsf:assembleNormalDebug \
  :mipush:assembleDebug \
  -PbuildSplits \
  "$@"

cleanup_submodule_lockfiles
