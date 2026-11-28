#!/bin/bash
# Creates the "crapi-credentials" Secret referenced by the manifests in this folder.
# Values come from the environment (DB_PASSWORD, MONGO_DB_PASSWORD, SECRET_KEY,
# API_GATEWAY_PASSWORD, SMTP_PASS); any that are unset are generated randomly.
# ADMIN_PASSWORD (optional) sets the seeded demo admin password used by identity and the chatbot;
# when unset it stays empty and both services use the documented demo password.
# The Secret is only created when it does not exist yet, so re-running never
# rotates a password that an existing database volume already uses.
# Usage: create-secrets.sh <namespace>
set -e
NAMESPACE="${1:-crapi}"

if kubectl get secret crapi-credentials -n "$NAMESPACE" >/dev/null 2>&1; then
  echo "Secret crapi-credentials already exists in namespace $NAMESPACE; leaving it unchanged."
  exit 0
fi

gen() { openssl rand -hex 16; }

kubectl create secret generic crapi-credentials -n "$NAMESPACE" \
  --from-literal=DB_PASSWORD="${DB_PASSWORD:-$(gen)}" \
  --from-literal=MONGO_DB_PASSWORD="${MONGO_DB_PASSWORD:-$(gen)}" \
  --from-literal=SECRET_KEY="${SECRET_KEY:-$(gen)}" \
  --from-literal=API_GATEWAY_PASSWORD="${API_GATEWAY_PASSWORD:-$(gen)}" \
  --from-literal=ADMIN_PASSWORD="${ADMIN_PASSWORD:-}" \
  --from-literal=SMTP_PASS="${SMTP_PASS:-}"
