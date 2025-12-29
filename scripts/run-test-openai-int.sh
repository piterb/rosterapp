#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

read_env_var() {
  local key="$1"
  if command -v rg >/dev/null 2>&1; then
    rg --no-line-number "^${key}=" .env | head -n 1 | sed "s/^${key}=//" || true
  else
    grep -E "^${key}=" .env | head -n 1 | sed "s/^${key}=//" || true
  fi
}

if [ -f .env ]; then
  OPENAI_API_KEY="$(read_env_var "OPENAI_API_KEY")"
  if [ -n "$OPENAI_API_KEY" ]; then
    export OPENAI_API_KEY
  fi
  if [ -z "${OPENAI_ENABLE_CACHE:-}" ]; then
    OPENAI_ENABLE_CACHE="$(read_env_var "OPENAI_ENABLE_CACHE")"
  fi
  if [ -n "${OPENAI_ENABLE_CACHE:-}" ]; then
    export OPENAI_ENABLE_CACHE
  else
    unset OPENAI_ENABLE_CACHE
  fi
fi

./gradlew openaiIT --rerun-tasks --info
