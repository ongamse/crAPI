#!/bin/sh
# Builds server.p12 from server.crt/server.key in the current directory.
# The keystore password is supplied via KEYSTORE_PASSWORD; never commit the
# generated key material.
set -e
if [ -z "$KEYSTORE_PASSWORD" ]; then
  echo "KEYSTORE_PASSWORD must be set" >&2
  exit 1
fi
rm -f server.p12
rm -f server.keystore
openssl pkcs12 -export -in server.crt -inkey server.key -out server.p12 -name identity -passout env:KEYSTORE_PASSWORD
keytool -importkeystore -deststorepass:env KEYSTORE_PASSWORD -destkeypass:env KEYSTORE_PASSWORD -destkeystore server.keystore -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass:env KEYSTORE_PASSWORD -alias identity
