#!/bin/bash
cd "$(dirname $0)"
kubectl create namespace crapi
#kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

# The JWKS signing key is not committed. Put your own jwks.json in ../keys to use it;
# otherwise an empty secret is created and the identity service generates an ephemeral key.
if [ -f ../keys/jwks.json ]; then
  kubectl create -n secret generic jwt-key-secret --from-file=../keys
else
  kubectl create -n secret generic jwt-key-secret
fi
# Generate the credentials at deploy time instead of committing them.
# Override by exporting DB_PASSWORD / MONGO_DB_PASSWORD / SECRET_KEY / API_GATEWAY_PASSWORD.
DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -hex 16)}"
MONGO_DB_PASSWORD="${MONGO_DB_PASSWORD:-$(openssl rand -hex 16)}"
SECRET_KEY="${SECRET_KEY:-$(openssl rand -hex 16)}"
API_GATEWAY_PASSWORD="${API_GATEWAY_PASSWORD:-$(openssl rand -hex 16)}"
kubectl create -n crapi secret generic crapi-secrets \
  --from-literal=POSTGRES_PASSWORD="$DB_PASSWORD" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MONGO_DB_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MH_MONGO_URI="admin:$MONGO_DB_PASSWORD@mongodb:27017" \
  --from-literal=SECRET_KEY="$SECRET_KEY" \
  --from-literal=API_GATEWAY_PASSWORD="$API_GATEWAY_PASSWORD"

kubectl apply -n crapi -f ./rbac
kubectl apply -n crapi -f ./mongodb
kubectl apply -n crapi -f ./postgres
kubectl apply -n crapi -f ./mailhog
kubectl apply -n crapi -f ./identity
kubectl apply -n crapi -f ./community
kubectl apply -n crapi -f ./workshop
kubectl apply -n crapi -f ./web
kubectl apply -n crapi -f ./chatbot
