#!/usr/bin/env bash
# Generates random crAPI credentials into .env.credentials (git-ignored) for
# docker-compose.yml. Existing values in the environment are honoured, and an
# existing .env.credentials is never overwritten.
#
# It also generates a self-signed TLS certificate and private key into ./certs
# (git-ignored, mounted into the web, community, workshop and chatbot containers
# at /app/certs). An existing key pair in ./certs is never overwritten. To use
# your own certificate, place server.crt and server.key in ./certs beforehand.
#
# Usage:
#   ./generate-credentials.sh
#   set -a; . ./.env.credentials; set +a
#   docker compose -f docker-compose.yml --compatibility up -d
set -euo pipefail

cd "$(dirname "$0")"
OUT="${1:-.env.credentials}"

CERT_DIR="${CERT_DIR:-certs}"

umask 077
if [ -f "$CERT_DIR/server.key" ] && [ -f "$CERT_DIR/server.crt" ]; then
  echo "$CERT_DIR/server.key already exists; leaving it unchanged."
else
  mkdir -p "$CERT_DIR"
  openssl req -x509 -newkey rsa:4096 -nodes -sha256 -days 365 \
    -keyout "$CERT_DIR/server.key" -out "$CERT_DIR/server.crt" \
    -subj "/CN=crapi" \
    -addext "subjectAltName=DNS:localhost,DNS:crapi-web,DNS:crapi-identity,DNS:crapi-community,DNS:crapi-workshop,DNS:crapi-chatbot,IP:127.0.0.1"
  chmod 644 "$CERT_DIR/server.crt"
  echo "Wrote $CERT_DIR/server.crt and $CERT_DIR/server.key"
fi

if [ -f "$OUT" ]; then
  echo "$OUT already exists; leaving it unchanged."
  exit 0
fi

{
  echo "DB_PASSWORD=${DB_PASSWORD:-$(openssl rand -hex 16)}"
  echo "MONGO_DB_PASSWORD=${MONGO_DB_PASSWORD:-$(openssl rand -hex 16)}"
  echo "JWT_SECRET=${JWT_SECRET:-$(openssl rand -hex 32)}"
  echo "SECRET_KEY=${SECRET_KEY:-$(openssl rand -hex 32)}"
  echo "API_GATEWAY_PASSWORD=${API_GATEWAY_PASSWORD:-$(openssl rand -hex 16)}"
  echo "MECHANIC_DEFAULT_PASSWORD=${MECHANIC_DEFAULT_PASSWORD:-$(openssl rand -hex 12)}"
} > "$OUT"
echo "Wrote $OUT"
