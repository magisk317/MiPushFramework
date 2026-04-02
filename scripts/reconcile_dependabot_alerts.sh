#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

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

OVERRIDES_FILE="${ROOT_DIR}/gradle/security-overrides.properties"
if [[ ! -f "${OVERRIDES_FILE}" ]]; then
  echo "Missing ${OVERRIDES_FILE}" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
ALERTS_FILE="${TMP_DIR}/alerts.ndjson"
MAP_FILE="${TMP_DIR}/overrides.map"

awk -F= '
  /^[[:space:]]*#/ {next}
  /^[[:space:]]*$/ {next}
  NF>=2 {
    key=$1
    val=$2
    gsub(/^[[:space:]]+|[[:space:]]+$/, "", key)
    gsub(/^[[:space:]]+|[[:space:]]+$/, "", val)
    print key "\t" val
  }
' "${OVERRIDES_FILE}" > "${MAP_FILE}"

gh api --paginate "/repos/${REPO}/dependabot/alerts?state=open&per_page=100" --jq '.[]' > "${ALERTS_FILE}"

to_number_like() {
  local v="${1:-}"
  v="${v#>=}"
  v="${v#>}"
  v="${v#=}"
  v="${v#~>}"
  v="${v#^}"
  echo "${v}"
}

version_gte() {
  local current
  local required
  current="$(to_number_like "${1}")"
  required="$(to_number_like "${2}")"
  [[ -z "${current}" || -z "${required}" ]] && return 1
  local top
  top="$(printf "%s\n%s\n" "${current}" "${required}" | sort -V | tail -n1)"
  [[ "${top}" == "${current}" ]]
}

covered=0
uncovered=0

while IFS= read -r line; do
  id="$(echo "${line}" | jq -r '.number')"
  pkg="$(echo "${line}" | jq -r '.dependency.package.name')"
  severity="$(echo "${line}" | jq -r '.security_vulnerability.severity')"
  patched="$(echo "${line}" | jq -r '.security_vulnerability.first_patched_version.identifier // ""')"
  override="$(awk -F'\t' -v p="${pkg}" '$1==p {print $2}' "${MAP_FILE}" | head -n1)"

  if [[ -z "${patched}" || -z "${override}" ]]; then
    echo "UNMATCHED #${id} ${pkg} (severity=${severity}, patched=${patched:-n/a}, override=${override:-n/a})"
    uncovered=$((uncovered + 1))
    continue
  fi

  if version_gte "${override}" "${patched}"; then
    covered=$((covered + 1))
    echo "COVERED #${id} ${pkg}: override=${override}, patched=${patched}, severity=${severity}"
  else
    echo "STALE_OVERRIDE #${id} ${pkg}: override=${override}, patched=${patched}, severity=${severity}"
    uncovered=$((uncovered + 1))
  fi
done < "${ALERTS_FILE}"

{
  echo "## Dependabot alert reconciliation"
  echo ""
  echo "- Open alerts scanned: $(wc -l < "${ALERTS_FILE}")"
  echo "- Covered by overrides: ${covered}"
  echo "- Not covered: ${uncovered}"
  echo "- Auto-dismiss: disabled"
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

echo "Reconciliation done (covered=${covered}, uncovered=${uncovered}, auto-dismiss=disabled)"
