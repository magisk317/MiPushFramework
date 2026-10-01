#!/usr/bin/env bash
# Downloads the latest ANIP (Android Notification Icon Project) release bundle
# and places it in xmsf/shell/src/main/assets/anip-bundle.zip.
#
# Fault-tolerant: if the download fails (network issues, GitHub rate limiting,
# etc.), the existing pre-committed bundle is preserved and the build continues.
#
# Usage:
#   scripts/update_anip_bundle.sh [asset_dir]
#   asset_dir defaults to xmsf/shell/src/main/assets

set -euo pipefail

REPO="${ANIP_REPO:-BetterAndroid/android-notification-icon-project}"
ASSET_DIR="${1:-xmsf/shell/src/main/assets}"
BUNDLE_NAME="anip-bundle.zip"
TARGET="$ASSET_DIR/$BUNDLE_NAME"
GITHUB_HOST="${ANIP_GITHUB_HOST:-https://github.com}"
API_HOST="${ANIP_API_HOST:-https://api.github.com}"

echo "==> Updating ANIP bundle from $REPO ..."

# ------------------------------------------------------------------
# 1. Query the latest release from GitHub Releases API
# ------------------------------------------------------------------
RELEASE_API="$API_HOST/repos/$REPO/releases/latest"
CURL_OPTS=(--connect-timeout 10 --max-time 30 -sSL)

# Add GitHub token if available (avoids rate limiting on CI runners)
if [ -n "${GITHUB_TOKEN:-}" ]; then
    CURL_OPTS+=(-H "Authorization: token $GITHUB_TOKEN")
elif [ -n "${CI_JOB_TOKEN:-}" ]; then
    # GitLab CI may not have a GitHub token; skip auth header
    true
fi

RELEASE_JSON=$(curl "${CURL_OPTS[@]}" "$RELEASE_API" 2>/dev/null) || {
    echo "WARN: Failed to query GitHub Releases API; keeping existing bundle."
    exit 0
}

# ------------------------------------------------------------------
# 2. Find the bundle asset download URL
# ------------------------------------------------------------------
ASSET_URL=$(python3 -c "
import sys, json
data = json.loads('''$RELEASE_JSON'''.replace(\"'''\", \"\"))
# Prefer exact 'anip-bundle' match, then any .zip asset
for a in data.get('assets', []):
    n = a.get('name', '')
    if 'anip-bundle' in n.lower() and n.endswith('.zip'):
        print(a['browser_download_url']); sys.exit(0)
for a in data.get('assets', []):
    n = a.get('name', '')
    if n.endswith('.zip') and ('icon' in n.lower() or 'anip' in n.lower()):
        print(a['browser_download_url']); sys.exit(0)
" 2>/dev/null) || true

if [ -z "$ASSET_URL" ]; then
    echo "WARN: No suitable ANIP bundle asset found in latest release; keeping existing bundle."
    exit 0
fi

# ------------------------------------------------------------------
# 3. Download and validate
# ------------------------------------------------------------------
echo "==> Downloading: $ASSET_URL"
TMPFILE=$(mktemp "${TMPDIR:-/tmp}/anip-bundle-XXXXXX.zip")
trap 'rm -f "$TMPFILE"' EXIT

if curl -sSL --connect-timeout 15 --max-time 180 -o "$TMPFILE" -L "$ASSET_URL"; then
    # Verify it is a valid ZIP archive
    if file "$TMPFILE" | grep -qi 'zip'; then
        mkdir -p "$ASSET_DIR"
        mv -f "$TMPFILE" "$TARGET"
        SIZE=$(stat -c%s "$TARGET" 2>/dev/null || stat -f%z "$TARGET" 2>/dev/null || echo "?")
        echo "==> ANIP bundle updated successfully: $TARGET ($SIZE bytes)"
    else
        echo "WARN: Downloaded file is not a valid ZIP archive; keeping existing bundle."
    fi
else
    echo "WARN: Download failed; keeping existing bundle."
fi
