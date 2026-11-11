#!/bin/bash
# Creates the crapi-credentials Secret consumed by the crAPI workloads.
# Values are generated randomly unless supplied through the environment
# (DB_PASSWORD, MONGO_DB_PASSWORD, JWT_SECRET, SECRET_KEY,
# API_GATEWAY_PASSWORD, MECHANIC_DEFAULT_PASSWORD). Nothing is stored in git.
set -e

NAMESPACE="${NAMESPACE:-crapi}"

if kubectl -n "$NAMESPACE" get secret crapi-credentials >/dev/null 2>&1; then
  echo "Secret crapi-credentials already exists in namespace $NAMESPACE; leaving it unchanged."
  exit 0
fi

DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -hex 16)}"
MONGO_DB_PASSWORD="${MONGO_DB_PASSWORD:-$(openssl rand -hex 16)}"
JWT_SECRET="${JWT_SECRET:-$(openssl rand -hex 32)}"
SECRET_KEY="${SECRET_KEY:-$(openssl rand -hex 32)}"
API_GATEWAY_PASSWORD="${API_GATEWAY_PASSWORD:-$(openssl rand -hex 16)}"
MECHANIC_DEFAULT_PASSWORD="${MECHANIC_DEFAULT_PASSWORD:-$(openssl rand -hex 12)}"

kubectl create -n "$NAMESPACE" secret generic crapi-credentials \
  --from-literal=POSTGRES_PASSWORD="$DB_PASSWORD" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MONGO_DB_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MH_MONGO_URI="admin:${MONGO_DB_PASSWORD}@mongodb:27017" \
  --from-literal=JWT_SECRET="$JWT_SECRET" \
  --from-literal=SECRET_KEY="$SECRET_KEY" \
  --from-literal=API_GATEWAY_PASSWORD="$API_GATEWAY_PASSWORD" \
  --from-literal=MECHANIC_DEFAULT_PASSWORD="$MECHANIC_DEFAULT_PASSWORD"
