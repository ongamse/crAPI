#!/bin/bash
cd "$(dirname $0)"
kubectl create namespace crapi

# Credentials are not committed. Export these before running this script.
: "${DB_PASSWORD:?export DB_PASSWORD}"
: "${MONGO_DB_PASSWORD:?export MONGO_DB_PASSWORD}"
: "${SECRET_KEY:?export SECRET_KEY}"
: "${API_GATEWAY_PASSWORD:?export API_GATEWAY_PASSWORD}"
kubectl create -n crapi secret generic crapi-credentials \
  --from-literal=POSTGRES_PASSWORD="$DB_PASSWORD" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MONGO_DB_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MH_MONGO_URI="admin:${MONGO_DB_PASSWORD}@mongodb:27017" \
  --from-literal=SECRET_KEY="$SECRET_KEY" \
  --from-literal=API_GATEWAY_PASSWORD="$API_GATEWAY_PASSWORD" \
  --from-literal=TLS_KEYSTORE_PASSWORD="${TLS_KEYSTORE_PASSWORD:-}" \
  --from-literal=TLS_KEY_PASSWORD="${TLS_KEY_PASSWORD:-}"
#kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

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
