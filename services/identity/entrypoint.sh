#!/bin/sh
set -e

if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
  export JWKS
else
  echo "No JWKS key file mounted at /app/keys/jwks.json; an ephemeral signing key will be generated."
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar

exec "$@"
