#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

TOML_FILE="gradle/libs.versions.toml"
WRAPPER_FILE="gradle/wrapper/gradle-wrapper.properties"

read_version() {
  local key="$1"
  local value
  value=$(sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" "$TOML_FILE" | head -n 1)
  if [[ -z "$value" ]]; then
    echo "Missing version key: $key" >&2
    exit 1
  fi
  printf '%s' "$value"
}

badge_escape() {
  local raw="$1"
  raw="${raw//-/--}"
  raw="${raw// /_}"
  printf '%s' "$raw"
}

KOTLIN_VERSION=$(read_version "kotlin")
COMPOSE_BOM_VERSION=$(read_version "compose-bom-alpha")
AGP_VERSION=$(read_version "agp")
MIN_SDK_VERSION=$(read_version "minSdk")
TARGET_SDK_VERSION=$(read_version "targetSdk")
JAVA_VERSION=$(read_version "java")
GRADLE_VERSION=$(sed -nE 's/^distributionUrl=.*gradle-([0-9A-Za-z.-]+)-(bin|all)\.zip/\1/p' "$WRAPPER_FILE")
if [[ -z "$GRADLE_VERSION" ]]; then
  echo "Missing Gradle version in $WRAPPER_FILE" >&2
  exit 1
fi

KOTLIN_BADGE=$(badge_escape "$KOTLIN_VERSION")
COMPOSE_BADGE=$(badge_escape "$COMPOSE_BOM_VERSION")
AGP_BADGE=$(badge_escape "$AGP_VERSION")
GRADLE_BADGE=$(badge_escape "$GRADLE_VERSION")

KOTLIN_LINE="![Kotlin](https://img.shields.io/badge/Kotlin-${KOTLIN_BADGE}-7F52FF?logo=kotlin&logoColor=white)"
JAVA_LINE="![Java](https://img.shields.io/badge/Java-${JAVA_VERSION}%2B-E76F00?logo=openjdk&logoColor=white)"
GRADLE_LINE="![Gradle](https://img.shields.io/badge/Gradle-${GRADLE_BADGE}-02303A?logo=gradle&logoColor=white)"
COMPOSE_LINE="![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM_${COMPOSE_BADGE}-4285F4?logo=jetpackcompose&logoColor=white)"
ANDROID_LINE="![Android](https://img.shields.io/badge/Android-${MIN_SDK_VERSION}%2B%20%2F%20target%20${TARGET_SDK_VERSION}-3DDC84?logo=android&logoColor=white)"
AGP_LINE="![AGP](https://img.shields.io/badge/AGP-${AGP_BADGE}-3DDC84?logo=gradle&logoColor=white)"

tmp_file="$(mktemp)"
awk \
  -v android="$ANDROID_LINE" \
  -v kotlin="$KOTLIN_LINE" \
  -v java="$JAVA_LINE" \
  -v gradle="$GRADLE_LINE" \
  -v compose="$COMPOSE_LINE" \
  -v agp="$AGP_LINE" '
    /^!\[Android/ { print android; next }
    /^!\[Kotlin/ { print kotlin; next }
    /^!\[Java/ { print java; next }
    /^!\[Gradle/ { print gradle; next }
    /^!\[Jetpack Compose/ { print compose; print agp; next }
    /^!\[AGP/ { next }
    { print }
  ' README.md > "$tmp_file"
mv "$tmp_file" README.md

echo "README badges synced from $TOML_FILE and $WRAPPER_FILE"
