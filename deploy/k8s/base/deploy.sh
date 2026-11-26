#!/bin/bash
cd "$(dirname $0)"
kubectl create namespace crapi
# Credentials are never committed. Export these before running this script.
: "${DB_PASSWORD:?Set DB_PASSWORD}"
: "${MONGO_DB_PASSWORD:?Set MONGO_DB_PASSWORD}"
: "${WORKSHOP_SECRET_KEY:?Set WORKSHOP_SECRET_KEY}"
: "${API_GATEWAY_PASSWORD:?Set API_GATEWAY_PASSWORD}"
kubectl create -n crapi secret generic crapi-credentials \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=POSTGRES_PASSWORD="$DB_PASSWORD" \
  --from-literal=MONGO_DB_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MH_MONGO_URI="admin:${MONGO_DB_PASSWORD}@mongodb:27017" \
  --from-literal=SECRET_KEY="$WORKSHOP_SECRET_KEY" \
  --from-literal=API_GATEWAY_PASSWORD="$API_GATEWAY_PASSWORD"
#kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

[ -f ../keys/jwks.json ] || { echo "Provide ../keys/jwks.json (RSA signing key, see docs/setup.md)" >&2; exit 1; }

kubectl create -n secret generic jwt-key-secret --from-file=../keys
kubectl apply -n crapi -f ./rbac
kubectl apply -n crapi -f ./mongodb
kubectl apply -n crapi -f ./postgres
kubectl apply -n crapi -f ./mailhog
kubectl apply -n crapi -f ./identity
kubectl apply -n crapi -f ./community
kubectl apply -n crapi -f ./workshop
kubectl apply -n crapi -f ./web
kubectl apply -n crapi -f ./chatbot
