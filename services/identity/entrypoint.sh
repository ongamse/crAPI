#!/bin/sh
set -e

# The signing key is never baked into the image. Supply it either as a mounted JWKS file
# (/app/keys/jwks.json) or as a base64 encoded JWKS in the JWKS env var. When neither is
# present the identity service generates an ephemeral key at startup.
if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
  export JWKS
elif [ -n "$JWKS" ]; then
  echo "Using JWKS from environment."
  export JWKS
else
  echo "No JWKS supplied; identity will generate an ephemeral signing key."
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar

exec "$@"
