#!/bin/bash
cd "$(dirname $0)"
kubectl create namespace crapi
#kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

kubectl create -n secret generic jwt-key-secret --from-file=../keys

# Credentials are never committed. Supply them via the environment, or let this
# script generate random values for a fresh deployment. Keep the generated
# values (kubectl -n crapi get secret crapi-credentials) if you redeploy.
rand() { openssl rand -base64 24 | tr -d '/+=\n'; }
DB_PASSWORD="${DB_PASSWORD:-$(rand)}"
MONGO_DB_PASSWORD="${MONGO_DB_PASSWORD:-$(rand)}"
SECRET_KEY="${SECRET_KEY:-$(rand)}"
API_GATEWAY_PASSWORD="${API_GATEWAY_PASSWORD:-$(rand)}"
kubectl create -n crapi secret generic crapi-credentials \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=POSTGRES_PASSWORD="$DB_PASSWORD" \
  --from-literal=MONGO_DB_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MH_MONGO_URI="admin:${MONGO_DB_PASSWORD}@mongodb:27017" \
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
