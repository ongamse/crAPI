mkdir -p certs
openssl req -x509 -newkey rsa:4096 -keyout certs/server.key -out certs/server.crt -sha256 -days 3650 -nodes -subj "/C=XX/ST=StateName/L=CityName/O=CompanyName/OU=CompanySectionName/CN=$1"
# Only the public certificate is copied into the service directories. The private key
# stays in services/certs/ (git-ignored) and is supplied to the containers at runtime
# via TLS_KEY_FILE (docker compose) or the TLS key Secret (helm).
cp -v certs/server.crt workshop/certs
cp -v certs/server.crt identity/src/main/resources/certs
cp -v certs/server.crt chatbot/certs
cp -v certs/server.crt web/certs
cp -v certs/server.crt community/certs
cd ./identity/src/main/resources/certs/
# keystore.sh needs the key temporarily to build the PKCS12 keystore
cp -v ../../../../../certs/server.key server.key
# The keystore password is random (or taken from TLS_KEYSTORE_PASSWORD) and kept only in the
# git-ignored services/certs/ directory.
if [ -z "${TLS_KEYSTORE_PASSWORD:-}" ]; then
  TLS_KEYSTORE_PASSWORD="$(openssl rand -hex 16)"
fi
export TLS_KEYSTORE_PASSWORD
(umask 077; printf '%s\n' "$TLS_KEYSTORE_PASSWORD" > ../../../../../certs/keystore.password)
./keystore.sh
rm -f server.key
echo "Set TLS_KEYSTORE_PASSWORD to the content of services/certs/keystore.password when running with TLS_ENABLED=true"
echo "Set TLS_KEY_FILE to the absolute path of services/certs/server.key when running with TLS_ENABLED=true"
