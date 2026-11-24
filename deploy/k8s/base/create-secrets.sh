#!/bin/bash
# Creates the crapi-secrets Secret used by the base manifests.
# Values are taken from the environment when set, otherwise generated randomly.
# Nothing sensitive is stored in this repository.
#   DB_PASSWORD, MONGO_DB_PASSWORD, JWT_SECRET, SECRET_KEY,
#   API_GATEWAY_PASSWORD, MECHANIC_SEED_PASSWORD

NAMESPACE="${1:-crapi}"
gen() { openssl rand -hex 16; }

DB_PASSWORD="${DB_PASSWORD:-$(gen)}"
MONGO_DB_PASSWORD="${MONGO_DB_PASSWORD:-$(gen)}"
JWT_SECRET="${JWT_SECRET:-$(gen)}"
SECRET_KEY="${SECRET_KEY:-$(gen)}"
API_GATEWAY_PASSWORD="${API_GATEWAY_PASSWORD:-$(gen)}"
MECHANIC_SEED_PASSWORD="${MECHANIC_SEED_PASSWORD:-$(gen)}"

kubectl create -n "${NAMESPACE}" secret generic crapi-secrets \
  --from-literal=DB_PASSWORD="${DB_PASSWORD}" \
  --from-literal=POSTGRES_PASSWORD="${DB_PASSWORD}" \
  --from-literal=MONGO_DB_PASSWORD="${MONGO_DB_PASSWORD}" \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="${MONGO_DB_PASSWORD}" \
  --from-literal=MH_MONGO_URI="admin:${MONGO_DB_PASSWORD}@mongodb:27017" \
  --from-literal=JWT_SECRET="${JWT_SECRET}" \
  --from-literal=SECRET_KEY="${SECRET_KEY}" \
  --from-literal=API_GATEWAY_PASSWORD="${API_GATEWAY_PASSWORD}" \
  --from-literal=MECHANIC_SEED_PASSWORD="${MECHANIC_SEED_PASSWORD}"
