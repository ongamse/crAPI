# Generates a TLS key pair for local use. The private key and keystore are written
# to ./tls (git-ignored) and must be mounted into the containers at runtime
# (see deploy/docker/docker-compose.yml, TLS_KEY_DIR). Never commit them.
# Usage: TLS_KEYSTORE_PASSWORD=... ./gencert.sh <common-name>
mkdir -p certs tls
openssl req -x509 -newkey rsa:4096 -keyout tls/server.key -out certs/server.crt -sha256 -days 3650 -nodes -subj "/C=XX/ST=StateName/L=CityName/O=CompanyName/OU=CompanySectionName/CN=$1"
cp -v certs/server.crt workshop/certs
cp -v certs/server.crt identity/src/main/resources/certs
cp -v certs/server.crt chatbot/certs
cp -v certs/server.crt web/certs
cp -v certs/server.crt community/certs
./identity/src/main/resources/certs/keystore.sh certs/server.crt tls/server.key tls
