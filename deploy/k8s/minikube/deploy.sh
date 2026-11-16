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

kubectl create -n crapi secret generic jwt-key-secret --from-file=../keys

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
