#!/bin/sh
set -e

# The private RSA JWKS used to sign JWTs is never baked into the image or committed.
# Supply it at runtime either as a mounted file (/app/keys/jwks.json) or as a
# base64-encoded JWKS environment variable (JWKS).
if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
elif [ -z "${JWKS:-}" ]; then
  echo "No JWKS key supplied: mount a JWKS file at /app/keys/jwks.json or set JWKS" >&2
  exit 1
fi
export JWKS
java -jar /app/identity-service-1.0-SNAPSHOT.jar

exec "$@"
