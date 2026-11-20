#!/bin/sh
set -e

if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
else
  echo "No JWKS key file found; the identity service will generate a signing key at startup."
  JWKS=""
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar --app.jwksJson=$JWKS

exec "$@"
