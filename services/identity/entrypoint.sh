#!/bin/sh
set -e

# The JWKS signing key is not baked into the image. Mount your own jwks.json at
# /app/keys/jwks.json; otherwise the identity service generates an ephemeral key at startup.
if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
  export JWKS
else
  echo "No /app/keys/jwks.json mounted; the service will generate an ephemeral JWKS key."
  unset JWKS
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar

exec "$@"
