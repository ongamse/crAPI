#!/usr/bin/env bash
# Generates random crAPI credentials into .env.credentials (git-ignored) for
# docker-compose.yml. Existing values in the environment are honoured, and an
# existing .env.credentials is never overwritten.
#
# Usage:
#   ./generate-credentials.sh
#   set -a; . ./.env.credentials; set +a
#   docker compose -f docker-compose.yml --compatibility up -d
set -euo pipefail

cd "$(dirname "$0")"
OUT="${1:-.env.credentials}"

if [ -f "$OUT" ]; then
  echo "$OUT already exists; leaving it unchanged."
  exit 0
fi

umask 077
{
  echo "DB_PASSWORD=${DB_PASSWORD:-$(openssl rand -hex 16)}"
  echo "MONGO_DB_PASSWORD=${MONGO_DB_PASSWORD:-$(openssl rand -hex 16)}"
  echo "JWT_SECRET=${JWT_SECRET:-$(openssl rand -hex 32)}"
  echo "SECRET_KEY=${SECRET_KEY:-$(openssl rand -hex 32)}"
  echo "API_GATEWAY_PASSWORD=${API_GATEWAY_PASSWORD:-$(openssl rand -hex 16)}"
  echo "MECHANIC_DEFAULT_PASSWORD=${MECHANIC_DEFAULT_PASSWORD:-$(openssl rand -hex 12)}"
} > "$OUT"
echo "Wrote $OUT"
