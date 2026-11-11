#!/bin/sh
set -e

# The private JWKS is supplied at runtime (mounted keys/jwks.json) and is never committed.
# Without it the identity service generates an ephemeral signing key at startup.
if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
else
  echo "No JWKS key file mounted; an ephemeral signing key will be generated."
  JWKS=""
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar --app.jwksJson="$JWKS"

exec "$@"
