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

# The JWKS signing key is not committed. Put your own jwks.json in ../keys to use it;
# otherwise an empty secret is created and the identity service generates an ephemeral key.
if [ -f ../keys/jwks.json ]; then
  kubectl create -n crapi secret generic jwt-key-secret --from-file=../keys
else
  kubectl create -n crapi secret generic jwt-key-secret
fi
# Generate the credentials at deploy time instead of committing them.
# Override by exporting DB_PASSWORD / MONGO_DB_PASSWORD / SECRET_KEY / API_GATEWAY_PASSWORD.
DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -hex 16)}"
MONGO_DB_PASSWORD="${MONGO_DB_PASSWORD:-$(openssl rand -hex 16)}"
SECRET_KEY="${SECRET_KEY:-$(openssl rand -hex 16)}"
API_GATEWAY_PASSWORD="${API_GATEWAY_PASSWORD:-$(openssl rand -hex 16)}"
kubectl create -n crapi secret generic crapi-secrets \
  --from-literal=POSTGRES_PASSWORD="$DB_PASSWORD" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MONGO_DB_PASSWORD="$MONGO_DB_PASSWORD" \
  --from-literal=MH_MONGO_URI="admin:$MONGO_DB_PASSWORD@mongodb:27017" \
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
