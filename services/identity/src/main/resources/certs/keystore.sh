#!/bin/sh
# Usage: ./keystore.sh <path-to-private-key>
# The private key is not stored in this directory; pass the path to it.
KEY_FILE="${1:?Usage: keystore.sh <path-to-server.key>}"
rm -f server.p12
rm -f server.keystore
openssl pkcs12 -export -in server.crt -inkey "$KEY_FILE" -out server.p12 -name identity -passout pass:passw0rd
keytool -importkeystore -deststorepass passw0rd -destkeypass passw0rd -destkeystore server.keystore -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass passw0rd -alias identity
