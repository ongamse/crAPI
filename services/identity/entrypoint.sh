#!/bin/sh
set -e

# The JWKS signing key (contains RSA private components) is never baked into the
# image or committed to git. Provide it at runtime, either as a mounted file
# (/app/keys/jwks.json for docker, /.keys/jwks.json for k8s/helm secret mounts)
# or as a base64 encoded JWKS in the JWKS environment variable.
if [ -f /app/keys/jwks.json ]; then
  echo "Loading JWKS key file /app/keys/jwks.json"
  JWKS=$(openssl base64 -in /app/keys/jwks.json -A)
elif [ -f /.keys/jwks.json ]; then
  echo "Loading JWKS key file /.keys/jwks.json"
  JWKS=$(openssl base64 -in /.keys/jwks.json -A)
elif [ -n "${JWKS:-}" ]; then
  echo "Loading JWKS from the JWKS environment variable."
else
  echo "No JWKS key provided: mount jwks.json at /app/keys/jwks.json (see docs/setup.md) or set JWKS." >&2
  exit 1
fi
java -jar /app/identity-service-1.0-SNAPSHOT.jar --app.jwksJson=$JWKS

exec "$@"
