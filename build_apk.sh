#!/usr/bin/env bash
set -euo pipefail

if ! command -v gradle >/dev/null 2>&1; then
  echo "[ERROR] gradle not found. Please install Gradle or add Gradle Wrapper files (gradlew + gradle/wrapper)."
  exit 1
fi

echo "[INFO] Building debug APK..."
gradle :app:assembleDebug

echo "[OK] APK output (expected): app/build/outputs/apk/debug/app-debug.apk"
