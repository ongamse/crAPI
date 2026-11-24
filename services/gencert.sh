#!/bin/sh
# Generates a local self-signed TLS certificate, key and (for identity) a PKCS12
# keystore into services/certs (git-ignored). Nothing is copied into the service
# source trees: the private key must never be committed. Mount the output
# directory into the containers (see TLS_CERTS_DIR in deploy/docker/docker-compose.yml).
#
# Usage: KEYSTORE_PASSWORD=<password> ./gencert.sh <common-name>
set -e
if [ -z "$1" ] || [ -z "$KEYSTORE_PASSWORD" ]; then
  echo "Usage: KEYSTORE_PASSWORD=<password> $0 <common-name>" >&2
  exit 1
fi
mkdir -p certs
openssl req -x509 -newkey rsa:4096 -keyout certs/server.key -out certs/server.crt -sha256 -days 3650 -nodes -subj "/C=XX/ST=StateName/L=CityName/O=CompanyName/OU=CompanySectionName/CN=$1"
chmod 600 certs/server.key
cd ./certs
../identity/src/main/resources/certs/keystore.sh
