#!/bin/sh
set -e

# The JWKS (RSA signing key) is never baked into the image. Mount a jwks.json at
# /app/keys/jwks.json or provide a base64 encoded JWKS through the JWKS environment variable.
# When neither is supplied the identity service generates an ephemeral signing key at startup.
if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
elif [ -f /.keys/jwks.json ]; then
  echo "Loading JWKS key file /.keys/jwks.json"
  JWKS=$(openssl base64 -in /.keys/jwks.json -A)
elif [ -n "$JWKS" ]; then
  echo "Loading JWKS from the JWKS environment variable."
else
  echo "No JWKS supplied, identity service will generate an ephemeral signing key."
  JWKS=""
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar --app.jwksJson="$JWKS"

exec "$@"
