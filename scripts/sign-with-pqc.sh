#!/usr/bin/env bash
# PQC Hybrid APK Signing Script
# Uses V3 key rotation with classical + ML-DSA (PQC) key lineage
#
# Requirements:
#   - Java 26+ (for ML-DSA key generation via keytool)
#   - Android build-tools 37.0.0+ (for apksigner V3 rotation + PQC support)
#
# Usage:
#   ./scripts/sign-with-pqc.sh <input.apk> <output.apk> [options]
#
# Options:
#   --keystore-dir <dir>    Keystore directory (default: keystore/ in project root)
#   --storepass <pass>      Keystore password (default: from STORE_PASS env, or "changeit")
#   --cert-dname <dname>    Certificate distinguished name
#   -h, --help              Show help
#
# Workflow:
#   1. Generate classical (EC) + PQC (ML-DSA-65) key pairs
#   2. Create signing lineage (classical -> PQC rotation)
#   3. Sign APK with classical key + lineage (V3 rotation)
#
# The PQC key is embedded in the signing lineage, enabling future devices
# (Android 17+) to verify using the quantum-safe key. The classical key
# provides backward compatibility with all Android versions.
#
# NOTE: Full V3.2 hybrid signing (both keys actively signing) requires
# a future build-tools update. Current approach uses V3 key rotation.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Default paths
KEYSTORE_DIR="${PROJECT_DIR}/keystore"
CLASSICAL_KEYSTORE="${KEYSTORE_DIR}/release.jks"
PQC_KEYSTORE="${KEYSTORE_DIR}/release-pqc.jks"
LINEAGE_FILE="${KEYSTORE_DIR}/signing-lineage"

# Default passwords (override via env or args)
STORE_PASS="${STORE_PASS:-changeit}"
CLASSICAL_ALIAS="${CLASSICAL_ALIAS:-release-classical}"
PQC_ALIAS="${PQC_ALIAS:-release-pqc}"

# Default certificate info
CERT_DNAME="${CERT_DNAME:-CN=MiPushFramework, OU=Development, O=Magisk317, L=Unknown, ST=Unknown, C=CN}"
CERT_VALIDITY="${CERT_VALIDITY:-36500}"  # ~100 years

# apksigner path
APKSIGNER="${ANDROID_HOME:-${HOME}/development/android-sdk}/build-tools/37.0.0/apksigner"

usage() {
    echo "Usage: $0 <input.apk> <output.apk> [options]"
    echo ""
    echo "Options:"
    echo "  --keystore-dir <dir>    Keystore directory (default: ${KEYSTORE_DIR})"
    echo "  --storepass <pass>      Keystore password (default: from STORE_PASS env)"
    echo "  --cert-dname <dname>    Certificate distinguished name"
    echo "  -h, --help              Show this help"
    exit 1
}

# Parse arguments
INPUT_APK=""
OUTPUT_APK=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --keystore-dir)
            KEYSTORE_DIR="$2"
            CLASSICAL_KEYSTORE="${KEYSTORE_DIR}/release.jks"
            PQC_KEYSTORE="${KEYSTORE_DIR}/release-pqc.jks"
            LINEAGE_FILE="${KEYSTORE_DIR}/signing-lineage"
            shift 2 ;;
        --storepass) STORE_PASS="$2"; shift 2 ;;
        --cert-dname) CERT_DNAME="$2"; shift 2 ;;
        -h|--help) usage ;;
        -*) echo "Unknown option: $1"; usage ;;
        *)
            if [[ -z "$INPUT_APK" ]]; then INPUT_APK="$1"
            elif [[ -z "$OUTPUT_APK" ]]; then OUTPUT_APK="$1"
            else echo "Unexpected argument: $1"; usage; fi
            shift ;;
    esac
done

if [[ -z "$INPUT_APK" ]] || [[ -z "$OUTPUT_APK" ]]; then
    echo "Error: input and output APK paths are required"
    usage
fi

if [[ ! -f "$INPUT_APK" ]]; then
    echo "Error: input APK not found: $INPUT_APK"
    exit 1
fi

if [[ ! -x "$APKSIGNER" ]]; then
    echo "Error: apksigner not found at: $APKSIGNER"
    echo "Install Android build-tools 37.0.0+ and set ANDROID_HOME"
    exit 1
fi

mkdir -p "$KEYSTORE_DIR"

# --- Step 1: Generate keys ---

if [[ ! -f "$CLASSICAL_KEYSTORE" ]]; then
    echo ">>> Generating new classical (EC) signing key..."
    keytool -genkeypair \
        -alias "$CLASSICAL_ALIAS" \
        -keyalg EC \
        -groupname secp256r1 \
        -sigalg SHA256withECDSA \
        -dname "$CERT_DNAME" \
        -validity "$CERT_VALIDITY" \
        -keystore "$CLASSICAL_KEYSTORE" \
        -storepass "$STORE_PASS" \
        -keypass "$STORE_PASS"
    echo "    Classical key saved to: $CLASSICAL_KEYSTORE"
else
    echo ">>> Using existing classical key: $CLASSICAL_KEYSTORE"
fi

if [[ ! -f "$PQC_KEYSTORE" ]]; then
    echo ">>> Generating new ML-DSA-65 (PQC) signing key..."
    keytool -genkeypair \
        -alias "$PQC_ALIAS" \
        -keyalg ML-DSA \
        -sigalg ML-DSA \
        -dname "$CERT_DNAME" \
        -validity "$CERT_VALIDITY" \
        -keystore "$PQC_KEYSTORE" \
        -storepass "$STORE_PASS" \
        -keypass "$STORE_PASS"
    echo "    PQC key saved to: $PQC_KEYSTORE"
else
    echo ">>> Using existing PQC key: $PQC_KEYSTORE"
fi

# --- Step 2: Create signing lineage (classical -> PQC) ---

if [[ ! -f "$LINEAGE_FILE" ]]; then
    echo ">>> Creating signing lineage (classical -> PQC rotation)..."
    "$APKSIGNER" rotate \
        --old-signer --ks "$CLASSICAL_KEYSTORE" --ks-key-alias "$CLASSICAL_ALIAS" \
            --ks-pass "pass:${STORE_PASS}" --key-pass "pass:${STORE_PASS}" \
        --new-signer --ks "$PQC_KEYSTORE" --ks-key-alias "$PQC_ALIAS" \
            --ks-pass "pass:${STORE_PASS}" --key-pass "pass:${STORE_PASS}" \
        --out "$LINEAGE_FILE"
    echo "    Lineage saved to: $LINEAGE_FILE"
else
    echo ">>> Using existing lineage: $LINEAGE_FILE"
fi

# --- Step 3: Sign APK ---

echo ">>> Signing APK with classical key + PQC lineage (V3 rotation)..."
echo "    Classical signer: ${CLASSICAL_KEYSTORE} (${CLASSICAL_ALIAS})"
echo "    PQC signer:       ${PQC_KEYSTORE} (${PQC_ALIAS})"
echo "    Lineage:          ${LINEAGE_FILE}"

"$APKSIGNER" sign \
    --ks "$CLASSICAL_KEYSTORE" \
    --ks-key-alias "$CLASSICAL_ALIAS" \
    --ks-pass "pass:${STORE_PASS}" \
    --key-pass "pass:${STORE_PASS}" \
    --lineage "$LINEAGE_FILE" \
    --out "$OUTPUT_APK" \
    "$INPUT_APK"

echo ">>> Signed APK written to: $OUTPUT_APK"

# Verify
echo ">>> Verifying signature..."
"$APKSIGNER" verify --verbose "$OUTPUT_APK" 2>&1 | grep -E "Verif|Signer|scheme"

echo ""
echo ">>> Done!"
echo ""
echo "NOTE: The APK is signed with V3 rotation. The PQC (ML-DSA) key is in the"
echo "signing lineage and will be used by Android 17+ devices for verification."
echo "The classical (EC) key provides backward compatibility with older devices."
