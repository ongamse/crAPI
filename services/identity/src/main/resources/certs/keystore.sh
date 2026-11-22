#!/bin/sh
# The keystore password is supplied by the caller and is not stored in the repository.
: "${TLS_KEYSTORE_PASSWORD:?Set TLS_KEYSTORE_PASSWORD in the environment}"
rm -f server.p12
rm -f server.keystore
openssl pkcs12 -export -in server.crt -inkey server.key -out server.p12 -name identity -passout env:TLS_KEYSTORE_PASSWORD
keytool -importkeystore -deststorepass:env TLS_KEYSTORE_PASSWORD -destkeypass:env TLS_KEYSTORE_PASSWORD -destkeystore server.keystore -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass:env TLS_KEYSTORE_PASSWORD -alias identity
