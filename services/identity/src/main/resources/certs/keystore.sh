#!/bin/sh
# Builds a PKCS12 keystore from a runtime-provided key/cert pair. Nothing generated here
# should be committed. Usage:
#   TLS_KEYSTORE_PASSWORD=<password> ./keystore.sh [path/to/server.crt] [path/to/server.key]
set -e
CRT="${1:-server.crt}"
KEY="${2:-server.key}"
if [ -z "$TLS_KEYSTORE_PASSWORD" ]; then
  echo "TLS_KEYSTORE_PASSWORD must be set" >&2
  exit 1
fi
rm -f server.p12
openssl pkcs12 -export -in "$CRT" -inkey "$KEY" -out server.p12 -name identity -passout env:TLS_KEYSTORE_PASSWORD
