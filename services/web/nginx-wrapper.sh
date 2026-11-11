#!/bin/sh

#
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

# if TLS_ENABLED is true or 1 then use https, otherwise use http
if [ "$TLS_ENABLED" = "true" ] || [ "$TLS_ENABLED" = "1" ]; then
    export HTTP_PROTOCOL=https
    export NGINX_TEMPLATE=/etc/nginx/conf.d/default.ssl.conf.template
else
    export HTTP_PROTOCOL=http
    export NGINX_TEMPLATE=/etc/nginx/conf.d/default.conf.template
fi
# The TLS private key is never baked into the image. It is normally mounted at
# /app/certs (see deploy/docker/generate-credentials.sh and the helm tls Secret).
# nginx's 443 listener needs a key pair to start, so when none was supplied
# generate a throwaway self-signed pair for this container only.
if [ ! -f /app/certs/server.key ] || [ ! -f /app/certs/server.crt ]; then
    echo "No TLS key pair mounted at /app/certs; generating an ephemeral self-signed certificate"
    mkdir -p /app/certs
    (umask 077 && openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 30 \
        -subj "/CN=crapi-web" \
        -keyout /app/certs/server.key -out /app/certs/server.crt) || exit 1
fi
ls -al /app/certs
env
envsubst '${HTTP_PROTOCOL} ${COMMUNITY_SERVICE} ${IDENTITY_SERVICE} ${WORKSHOP_SERVICE} ${CHATBOT_SERVICE} ${MAILHOG_WEB_SERVICE}' < $NGINX_TEMPLATE > /etc/nginx/conf.d/default.conf
openresty
exec "$@"