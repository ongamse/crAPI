#!/bin/sh
set -e

if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
  java -jar /app/identity-service-1.0-SNAPSHOT.jar --app.jwksJson="$JWKS"
else
  echo "No JWKS key file found; an ephemeral signing key will be generated."
  java -jar /app/identity-service-1.0-SNAPSHOT.jar
fi

exec "$@"
