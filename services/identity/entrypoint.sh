#!/bin/sh
set -e

# The RSA signing key (JWKS) is never baked into the image. It must be supplied
# at runtime, either as a mounted /app/keys/jwks.json file or as the JWKS
# environment variable (base64 encoded JWKS json).
if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
elif [ -n "${JWKS:-}" ]; then
  echo "Loading JWKS from the JWKS environment variable."
else
  echo "No JWKS key supplied. Mount /app/keys/jwks.json or set JWKS (base64 encoded JWKS json)." >&2
  exit 1
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar --app.jwksJson=$JWKS

exec "$@"
