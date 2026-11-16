mkdir -p certs
openssl req -x509 -newkey rsa:4096 -keyout certs/server.key -out certs/server.crt -sha256 -days 3650 -nodes -subj "/C=XX/ST=StateName/L=CityName/O=CompanyName/OU=CompanySectionName/CN=$1"
# The private key stays only in ./certs (git-ignored) and is mounted into containers at runtime
# (see deploy/docker/docker-compose.yml TLS_KEY_FILE). Only the public certificate is copied into service dirs.
cp -v certs/server.crt workshop/certs
cp -v certs/server.crt identity/src/main/resources/certs
cp -v certs/server.crt chatbot/certs
cp -v certs/server.crt web/certs
cp -v certs/server.crt community/certs
cd ./identity/src/main/resources/certs/
./keystore.sh ../../../../../certs/server.key
