#!/bin/bash

# Licensed under the Apache License, Version 2.0 (the “License”);
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an “AS IS” BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


cd "$(dirname $0)"
kubectl create namespace crapi

# Optional: export JWKS_FILE=/path/to/jwks.json to use a stable signing key (otherwise an ephemeral one is generated)
kubectl create -n crapi secret generic jwt-key-secret ${JWKS_FILE:+--from-file=jwks.json="$JWKS_FILE"}
kubectl create -n crapi secret generic crapi-secrets \
  --from-literal=DB_PASSWORD="${DB_PASSWORD:?Set DB_PASSWORD}" \
  --from-literal=MONGO_DB_PASSWORD="${MONGO_DB_PASSWORD:?Set MONGO_DB_PASSWORD}" \
  --from-literal=JWT_SECRET="${JWT_SECRET:?Set JWT_SECRET}" \
  --from-literal=SECRET_KEY="${SECRET_KEY:?Set SECRET_KEY}" \
  --from-literal=API_GATEWAY_PASSWORD="${API_GATEWAY_PASSWORD:?Set API_GATEWAY_PASSWORD}"
kubectl apply -n crapi -f ../base/rbac
kubectl apply -n crapi -f ../base/mongodb
kubectl apply -n crapi -f ../base/postgres
kubectl apply -n crapi -f ../base/mailhog
kubectl apply -n crapi -f ../base/identity
kubectl apply -n crapi -f ../base/community
kubectl apply -n crapi -f ../base/workshop
kubectl apply -n crapi -f ../base/web
kubectl apply -n crapi -f ./mailhog
kubectl apply -n crapi -f ./web
