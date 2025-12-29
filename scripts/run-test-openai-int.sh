#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ -f .env ]; then
  if command -v rg >/dev/null 2>&1; then
    OPENAI_API_KEY="$(rg -n "^OPENAI_API_KEY=" .env | sed "s/^OPENAI_API_KEY=//")"
  else
    OPENAI_API_KEY="$(grep -E "^OPENAI_API_KEY=" .env | sed "s/^OPENAI_API_KEY=//")"
  fi
  export OPENAI_API_KEY
fi

./gradlew openaiIT --rerun-tasks --info
