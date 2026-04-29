#!/usr/bin/env bash
set -euo pipefail

# Prefer user-provided JAVA_HOME; fallback to common local path used in this repo's dev container
export JAVA_HOME="${JAVA_HOME:-/root/.local/share/mise/installs/java/17.0.2}"
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "[ERROR] JAVA_HOME is invalid: $JAVA_HOME"
  echo "[HINT] Please set JAVA_HOME to a valid JDK 17+ path."
  exit 1
fi

if ! command -v gradle >/dev/null 2>&1; then
  echo "[ERROR] gradle not found. Please install Gradle or add Gradle Wrapper files (gradlew + gradle/wrapper)."
  exit 1
fi

echo "[INFO] Building debug APK..."
gradle :app:assembleDebug

echo "[OK] APK output (expected): app/build/outputs/apk/debug/app-debug.apk"
