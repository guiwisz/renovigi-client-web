#!/usr/bin/env bash
# Testa a API real com sua conta do RenoClient. A senha é lida sem eco e
# só existe na memória deste processo (não vai para o histórico do shell).
set -euo pipefail
cd "$(dirname "$0")/.."
read -rp "Usuário RenoClient: " SHINE_USER
read -rsp "Senha: " SHINE_PASS; echo
export SHINE_USER SHINE_PASS
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jdk-21}"
./gradlew :app:testDebugUnitTest --tests '*LiveProbe*' --rerun --console=plain 2>&1 \
  | grep -E '^\s*(✔|✘)|FAILED|BUILD'
