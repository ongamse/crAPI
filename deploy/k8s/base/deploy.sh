#!/bin/bash
cd "$(dirname $0)"
kubectl create namespace crapi
#kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

# Optional: export JWKS_FILE=/path/to/jwks.json to use a stable signing key (otherwise an ephemeral one is generated)
kubectl create -n crapi secret generic jwt-key-secret ${JWKS_FILE:+--from-file=jwks.json="$JWKS_FILE"}
kubectl create -n crapi secret generic crapi-secrets \
  --from-literal=DB_PASSWORD="${DB_PASSWORD:?Set DB_PASSWORD}" \
  --from-literal=MONGO_DB_PASSWORD="${MONGO_DB_PASSWORD:?Set MONGO_DB_PASSWORD}" \
  --from-literal=JWT_SECRET="${JWT_SECRET:?Set JWT_SECRET}" \
  --from-literal=SECRET_KEY="${SECRET_KEY:?Set SECRET_KEY}" \
  --from-literal=API_GATEWAY_PASSWORD="${API_GATEWAY_PASSWORD:?Set API_GATEWAY_PASSWORD}"
kubectl apply -n crapi -f ./rbac
kubectl apply -n crapi -f ./mongodb
kubectl apply -n crapi -f ./postgres
kubectl apply -n crapi -f ./mailhog
kubectl apply -n crapi -f ./identity
kubectl apply -n crapi -f ./community
kubectl apply -n crapi -f ./workshop
kubectl apply -n crapi -f ./web
kubectl apply -n crapi -f ./chatbot
