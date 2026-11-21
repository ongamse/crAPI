#!/bin/bash
cd "$(dirname $0)"
kubectl create namespace crapi

# Credentials are not stored in the manifests. Export these before running this script.
: "${DB_PASSWORD:?Set DB_PASSWORD}"
: "${MONGO_DB_PASSWORD:?Set MONGO_DB_PASSWORD}"
: "${SECRET_KEY:?Set SECRET_KEY}"
: "${API_GATEWAY_PASSWORD:?Set API_GATEWAY_PASSWORD}"
# Optional (add to the Secret only when exported): API_PASSWORD (chatbot login), MECHANIC_SEED_PASSWORD,
# TLS_KEYSTORE_PASSWORD and TLS_KEY_PASSWORD (identity TLS).
optional_args=()
for v in API_PASSWORD MECHANIC_SEED_PASSWORD TLS_KEYSTORE_PASSWORD TLS_KEY_PASSWORD; do
  if [ -n "${!v:-}" ]; then
    optional_args+=("--from-literal=$v=${!v}")
  fi
done
kubectl create -n crapi secret generic crapi-credentials \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=MONGO_DB_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=SECRET_KEY="$SECRET_KEY" \
  --from-literal=API_GATEWAY_PASSWORD="$API_GATEWAY_PASSWORD" \
  "${optional_args[@]}"
#kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml

# Optional: supply your own signing key by placing a jwks.json in ../keys. Without it the identity
# service generates an ephemeral RSA signing key at startup.
if [ -f ../keys/jwks.json ]; then
  kubectl create -n crapi secret generic jwt-signing-keys \
    --from-literal=JWKS="$(openssl base64 -in ../keys/jwks.json -A)"
fi
kubectl apply -n crapi -f ./rbac
kubectl apply -n crapi -f ./mongodb
kubectl apply -n crapi -f ./postgres
kubectl apply -n crapi -f ./mailhog
kubectl apply -n crapi -f ./identity
kubectl apply -n crapi -f ./community
kubectl apply -n crapi -f ./workshop
kubectl apply -n crapi -f ./web
kubectl apply -n crapi -f ./chatbot
