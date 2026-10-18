#!/bin/sh
set -e
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
URL="https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
EXPECTED="d64cdcd39c32c6a0e1c8e1b9f6c09b4a86a3188a5390d7cf2aab6f8f5f6e6f80"
if [ ! -f "$JAR" ]; then
  echo "Gradle Wrapper JAR ilk çalıştırma için indiriliyor..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "$URL" -o "$JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$JAR" "$URL"
  else
    echo "HATA: curl veya wget bulunamadı; gradle-wrapper.jar indirilemedi." >&2
    exit 1
  fi
fi
exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
