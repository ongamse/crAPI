mkdir -p certs
openssl req -x509 -newkey rsa:4096 -keyout certs/server.key -out certs/server.crt -sha256 -days 3650 -nodes -subj "/C=XX/ST=StateName/L=CityName/O=CompanyName/OU=CompanySectionName/CN=$1"
# Only the public certificate is copied into service build contexts. The private key stays in
# services/certs (git-ignored) and is mounted at runtime (docker compose: TLS_KEY_FILE=<abs path>/certs/server.key).
cp -v certs/server.crt workshop/certs
cp -v certs/server.crt identity/src/main/resources/certs
cp -v certs/server.crt chatbot/certs
cp -v certs/server.crt web/certs
cp -v certs/server.crt community/certs
cd ./identity/src/main/resources/certs/
TLS_KEY_SOURCE="$(cd ../../../../../certs && pwd)/server.key" ./keystore.sh
