#!/usr/bin/env bash
set -euo pipefail

./gradlew :push:assembleRelease -PbuildSplits=true "$@"
