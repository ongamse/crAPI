#!/bin/sh
# Generates a self-signed TLS certificate/key for local use. Private key material is
# never committed: it is written only to ./certs (git-ignored) and to the runtime
# directory that docker compose mounts at /app/certs (deploy/docker/certs, git-ignored).
#
# Usage: TLS_KEYSTORE_PASSWORD=<password> ./gencert.sh <common-name>
set -e
if [ -z "$1" ]; then
  echo "usage: TLS_KEYSTORE_PASSWORD=<password> $0 <common-name>" >&2
  exit 1
fi
if [ -z "$TLS_KEYSTORE_PASSWORD" ]; then
  echo "TLS_KEYSTORE_PASSWORD must be set (used for the identity service PKCS12 keystore)" >&2
  exit 1
fi

mkdir -p certs
openssl req -x509 -newkey rsa:4096 -keyout certs/server.key -out certs/server.crt -sha256 -days 3650 -nodes -subj "/C=XX/ST=StateName/L=CityName/O=CompanyName/OU=CompanySectionName/CN=$1"
openssl pkcs12 -export -in certs/server.crt -inkey certs/server.key -out certs/server.p12 -name identity -passout env:TLS_KEYSTORE_PASSWORD

# Public certificate only is copied into the service build contexts.
for d in workshop/certs identity/src/main/resources/certs chatbot/certs web/certs community/certs; do
  mkdir -p "$d"
  cp -v certs/server.crt "$d"/
done

# Runtime TLS material (key + keystore) for docker compose.
mkdir -p ../deploy/docker/certs
cp -v certs/server.crt certs/server.key certs/server.p12 ../deploy/docker/certs/
chmod 600 ../deploy/docker/certs/server.key
