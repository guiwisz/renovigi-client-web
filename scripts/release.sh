#!/usr/bin/env bash
# Gera o APK de release e copia para dist/Solar-<versão>.apk.
# Assinatura: keystore/keystore.properties (fora do git). Sem ela, o APK sai sem assinatura.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jdk-21}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
./gradlew :app:assembleRelease --console=plain -q
version=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
mkdir -p dist
src=$(ls app/build/outputs/apk/release/app-release*.apk | head -1)
cp "$src" "dist/Solar-$version.apk"
echo "dist/Solar-$version.apk ($(du -h "dist/Solar-$version.apk" | cut -f1))"
