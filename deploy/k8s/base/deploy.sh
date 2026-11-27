#!/bin/bash
cd "$(dirname $0)"
kubectl create namespace crapi

# Credentials are never committed: export these before running this script.
: "${DB_PASSWORD:?Set DB_PASSWORD}" "${MONGO_DB_PASSWORD:?Set MONGO_DB_PASSWORD}" \
  "${SECRET_KEY:?Set SECRET_KEY}" \
  "${API_GATEWAY_PASSWORD:?Set API_GATEWAY_PASSWORD}" \
  "${MECHANIC_DEFAULT_PASSWORD:?Set MECHANIC_DEFAULT_PASSWORD}"
kubectl create -n crapi secret generic crapi-credentials \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=MONGO_DB_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=SECRET_KEY="$SECRET_KEY" \
  --from-literal=API_GATEWAY_PASSWORD="$API_GATEWAY_PASSWORD" \
  --from-literal=MECHANIC_DEFAULT_PASSWORD="$MECHANIC_DEFAULT_PASSWORD"
#kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

kubectl create -n secret generic jwt-signing-keys --from-file=../keys
kubectl apply -n crapi -f ./rbac
kubectl apply -n crapi -f ./mongodb
kubectl apply -n crapi -f ./postgres
kubectl apply -n crapi -f ./mailhog
kubectl apply -n crapi -f ./identity
kubectl apply -n crapi -f ./community
kubectl apply -n crapi -f ./workshop
kubectl apply -n crapi -f ./web
kubectl apply -n crapi -f ./chatbot
