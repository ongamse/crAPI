#!/bin/bash
cd "$(dirname $0)"
kubectl create namespace crapi
#kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

# Optional: a full RSA JWKS (with private parts) in ../keys/jwks.json gives a stable signing key;
# otherwise the identity service generates an ephemeral key at startup.
if [ -d ../keys ]; then
  kubectl create -n secret secret generic jwt-key-secret --from-file=../keys
else
  kubectl create -n secret secret generic jwt-key-secret
fi
kubectl apply -n crapi -f ./rbac
# Credentials are never stored in git: export these before running this script.
: "${DB_PASSWORD:?Set DB_PASSWORD in the environment}"
: "${MONGO_DB_PASSWORD:?Set MONGO_DB_PASSWORD in the environment}"
: "${SECRET_KEY:?Set SECRET_KEY in the environment}"
: "${API_GATEWAY_PASSWORD:?Set API_GATEWAY_PASSWORD in the environment}"
kubectl create secret generic crapi-credentials -n crapi \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=POSTGRES_PASSWORD="$DB_PASSWORD" \
  --from-literal=MONGO_DB_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=SECRET_KEY="$SECRET_KEY" \
  --from-literal=API_GATEWAY_PASSWORD="$API_GATEWAY_PASSWORD"
kubectl apply -n crapi -f ./mongodb
kubectl apply -n crapi -f ./postgres
kubectl apply -n crapi -f ./mailhog
kubectl apply -n crapi -f ./identity
kubectl apply -n crapi -f ./community
kubectl apply -n crapi -f ./workshop
kubectl apply -n crapi -f ./web
kubectl apply -n crapi -f ./chatbot
