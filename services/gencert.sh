#!/bin/sh
# Generates a local TLS key pair in services/certs (git-ignored).
# The private key is never copied into a tracked service directory; mount the
# certs directory at runtime instead, e.g. TLS_CERTS_DIR=$PWD/certs docker compose up
# (see deploy/docker/docker-compose.yml). Only the public certificate is copied
# into the service images' certs folders.
set -e
mkdir -p certs
openssl req -x509 -newkey rsa:4096 -keyout certs/server.key -out certs/server.crt -sha256 -days 3650 -nodes -subj "/C=XX/ST=StateName/L=CityName/O=CompanyName/OU=CompanySectionName/CN=$1"
chmod 600 certs/server.key
cp -v certs/server.crt workshop/certs
cp -v certs/server.crt identity/src/main/resources/certs
cp -v certs/server.crt chatbot/certs
cp -v certs/server.crt web/certs
cp -v certs/server.crt community/certs
cp -v certs/server.crt gateway-service
# Build the identity keystore, using the key only temporarily.
cd ./identity/src/main/resources/certs/
cp ../../../../../certs/server.key server.key
./keystore.sh
rm -f server.key
