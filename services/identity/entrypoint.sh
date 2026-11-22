#!/bin/sh
set -e

if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
else
  echo "No JWKS key file mounted; using JWKS environment variable or an ephemeral signing key."
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar ${JWKS:+--app.jwksJson=$JWKS}

exec "$@"
