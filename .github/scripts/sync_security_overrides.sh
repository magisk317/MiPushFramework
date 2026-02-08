#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_FILE="${ROOT_DIR}/gradle/security-overrides.properties"
INIT_FILE="${ROOT_DIR}/gradle/security-overrides.init.gradle"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

CHECK_MODE="false"
if [[ "${1:-}" == "--check" ]]; then
  CHECK_MODE="true"
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "gh is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

REPO="${GITHUB_REPOSITORY:-}"
if [[ -z "${REPO}" ]]; then
  REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
fi

ALERTS_OBJ_FILE="${TMP_DIR}/alerts-objects.ndjson"
ALERTS_JSON_FILE="${TMP_DIR}/alerts.json"
MAP_FILE="${TMP_DIR}/overrides.map"
GEN_FILE="${TMP_DIR}/security-overrides.properties"
INIT_GEN_FILE="${TMP_DIR}/security-overrides.init.gradle"

gh api --paginate "/repos/${REPO}/dependabot/alerts?state=open&per_page=100" --jq '.[]' > "${ALERTS_OBJ_FILE}"
jq -s '.' "${ALERTS_OBJ_FILE}" > "${ALERTS_JSON_FILE}"

# Build strongest patched version for each vulnerable package.
jq -r '
  .[]
  | select(.dependency.package.ecosystem == "maven")
  | [
      .dependency.package.name,
      (
        .security_advisory.vulnerabilities
        | map(.first_patched_version.identifier // "")
        | map(select(length > 0))
        | first // ""
      )
    ]
  | select(.[1] != "")
  | @tsv
' "${ALERTS_JSON_FILE}" | while IFS=$'\t' read -r pkg ver; do
  if [[ -z "${pkg}" || -z "${ver}" ]]; then
    continue
  fi
  if [[ -f "${MAP_FILE}" ]]; then
    current="$(awk -F'\t' -v p="${pkg}" '$1==p {print $2}' "${MAP_FILE}" || true)"
  else
    current=""
  fi
  if [[ -z "${current}" ]]; then
    printf "%s\t%s\n" "${pkg}" "${ver}" >> "${MAP_FILE}"
    continue
  fi
  strongest="$(printf "%s\n%s\n" "${current}" "${ver}" | sort -V | tail -n1)"
  awk -F'\t' -v p="${pkg}" -v v="${strongest}" 'BEGIN{OFS="\t"} {if($1==p){$2=v} print}' "${MAP_FILE}" > "${MAP_FILE}.next"
  mv "${MAP_FILE}.next" "${MAP_FILE}"
done

{
  echo "# AUTO-GENERATED FILE. DO NOT EDIT MANUALLY."
  echo "# Source: GitHub Dependabot open alerts (maven ecosystem)."
  echo "# Regenerate: .github/scripts/sync_security_overrides.sh"
  echo
  if [[ -f "${MAP_FILE}" ]]; then
    sort -u "${MAP_FILE}" | while IFS=$'\t' read -r pkg ver; do
      echo "${pkg}=${ver}"
    done
  fi
} > "${GEN_FILE}"

cat > "${INIT_GEN_FILE}" <<'EOF'
// AUTO-GENERATED FILE. DO NOT EDIT MANUALLY.
// Source: gradle/security-overrides.properties
// Purpose: apply security overrides to project + buildscript classpaths in CI.

import java.util.Properties

def loadSecurityOverrides = { File baseDir ->
    File propsFile = new File(baseDir, "gradle/security-overrides.properties")
    if (!propsFile.exists()) {
        return [:]
    }
    Properties props = new Properties()
    propsFile.withReader("UTF-8") { props.load(it) }
    props.collectEntries { key, value -> [(key.toString()): value.toString()] }
}

def applySecurityOverrides = { strategy, Map<String, String> overrides ->
    strategy.eachDependency { details ->
        String key = "${details.requested.group}:${details.requested.name}"
        String forcedVersion = overrides[key]
        if (forcedVersion && details.requested.version != forcedVersion) {
            details.useVersion(forcedVersion)
            details.because("Security override from gradle/security-overrides.properties")
        }
    }
}

def securityOverrides = loadSecurityOverrides(gradle.startParameter.currentDir)

gradle.settingsEvaluated { settings ->
    settings.buildscript.configurations.configureEach { cfg ->
        applySecurityOverrides(cfg.resolutionStrategy, securityOverrides)
    }
}

gradle.beforeProject { project ->
    project.buildscript.configurations.configureEach { cfg ->
        applySecurityOverrides(cfg.resolutionStrategy, securityOverrides)
    }
    project.configurations.configureEach { cfg ->
        applySecurityOverrides(cfg.resolutionStrategy, securityOverrides)
    }
}
EOF

if [[ "${CHECK_MODE}" == "true" ]]; then
  if [[ ! -f "${OUTPUT_FILE}" ]]; then
    echo "Missing ${OUTPUT_FILE}. Run .github/scripts/sync_security_overrides.sh" >&2
    exit 1
  fi
  if [[ ! -f "${INIT_FILE}" ]]; then
    echo "Missing ${INIT_FILE}. Run .github/scripts/sync_security_overrides.sh" >&2
    exit 1
  fi
  if ! cmp -s "${GEN_FILE}" "${OUTPUT_FILE}"; then
    echo "Security overrides are stale. Run .github/scripts/sync_security_overrides.sh" >&2
    diff -u "${OUTPUT_FILE}" "${GEN_FILE}" || true
    exit 1
  fi
  if ! cmp -s "${INIT_GEN_FILE}" "${INIT_FILE}"; then
    echo "Security init script is stale. Run .github/scripts/sync_security_overrides.sh" >&2
    diff -u "${INIT_FILE}" "${INIT_GEN_FILE}" || true
    exit 1
  fi
  echo "Security overrides are up-to-date."
  exit 0
fi

mkdir -p "$(dirname "${OUTPUT_FILE}")"
cp "${GEN_FILE}" "${OUTPUT_FILE}"
cp "${INIT_GEN_FILE}" "${INIT_FILE}"
echo "Updated ${OUTPUT_FILE}"
echo "Updated ${INIT_FILE}"
