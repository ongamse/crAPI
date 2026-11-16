#!/bin/sh
set -e

# The JWKS signing key is never baked into the image. Supply it by mounting
# /app/keys/jwks.json or by passing a base64 encoded JWKS in the JWKS env var.
if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
elif [ -z "${JWKS:-}" ]; then
  echo "No JWKS key provided: mount /app/keys/jwks.json or set the JWKS env var." >&2
  exit 1
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar --app.jwksJson=$JWKS

exec "$@"
