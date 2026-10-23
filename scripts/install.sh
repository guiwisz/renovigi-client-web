#!/usr/bin/env bash
# Compila e instala no celular conectado por USB (depuração USB ativada).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jdk-21}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
./gradlew :app:installDebug --console=plain
"$ANDROID_HOME/platform-tools/adb" shell am start -n br.com.dhc.solar/.MainActivity
