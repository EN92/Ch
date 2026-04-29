#!/usr/bin/env bash
set -euo pipefail

# Prefer user-provided JAVA_HOME; otherwise auto-detect common Android Studio JBR paths.
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in \
    "$HOME/.local/share/mise/installs/java/17.0.2" \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/opt/android-studio/jbr" \
    "/c/Program Files/Android/Android Studio/jbr" \
    "/c/Program Files/Android/Android Studio/jre"; do
    if [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "${JAVA_HOME:-}" ]; then
  echo "[ERROR] JAVA_HOME is not set and no Android Studio JBR was auto-detected."
  echo "[HINT] Install Android Studio (default path) or set JAVA_HOME to JDK 17+."
  exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "[ERROR] JAVA_HOME is invalid: $JAVA_HOME"
  echo "[HINT] Please set JAVA_HOME to a valid JDK 17+ path."
  exit 1
fi

if [ -x "./gradlew" ]; then
  GRADLE_CMD="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
else
  echo "[ERROR] Neither ./gradlew nor gradle command was found."
  echo "[HINT] Recommended: generate Gradle Wrapper in Android Studio and commit wrapper files."
  exit 1
fi

echo "[INFO] Building debug APK..."
if ! "$GRADLE_CMD" :app:assembleDebug; then
  echo "[HINT] If you see 403/forbidden while resolving dependencies, check your network/proxy/repository access policy."
  echo "[HINT] Make sure JAVA_HOME points to JDK 17+ and Android SDK is configured in local.properties."
  exit 1
fi

echo "[OK] APK output (expected): app/build/outputs/apk/debug/app-debug.apk"
