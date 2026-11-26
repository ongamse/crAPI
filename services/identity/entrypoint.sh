#!/bin/sh
set -e

# The RSA signing key (JWKS) is never baked into the image. Provide it at runtime via
# one of: JWKS (base64 of the JSON), JWKS_FILE, /app/keys/jwks.json (docker compose
# mount) or /.keys/jwks.json (Kubernetes/Helm secret mount).
if [ -z "$JWKS" ]; then
  for f in "$JWKS_FILE" /app/keys/jwks.json /.keys/jwks.json; do
    if [ -n "$f" ] && [ -f "$f" ]; then
      echo "Loading JWKS key file $f"
      JWKS=$(openssl base64 -in "$f" -A)
      break
    fi
  done
fi
if [ -z "$JWKS" ]; then
  echo "No JWKS key provided. Set JWKS or mount a jwks.json (see docs/setup.md)." >&2
  exit 1
fi
export JWKS

exec java -jar /app/identity-service-1.0-SNAPSHOT.jar "$@"
