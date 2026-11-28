#!/bin/sh
set -e

if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
elif [ -n "$JWKS" ]; then
  echo "Using JWKS from environment."
else
  echo "No JWKS supplied; the identity service will generate an ephemeral signing key."
  JWKS=""
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar --app.jwksJson=$JWKS

exec "$@"
