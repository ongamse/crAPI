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
    if [ ! -s /app/certs/server.key ]; then
        echo "TLS_ENABLED is set but /app/certs/server.key is missing; mount the private key (see services/gencert.sh)" >&2
        exit 1
    fi
else
    export HTTP_PROTOCOL=http
    export NGINX_TEMPLATE=/etc/nginx/conf.d/default.conf.template
    # The TLS private key is not baked into the image. Without a mounted key, serve plain HTTP only.
    if [ ! -s /app/certs/server.key ]; then
        export NGINX_TEMPLATE=/etc/nginx/conf.d/default.nokey.conf.template
    fi
fi
ls -al /app/certs
env
envsubst '${HTTP_PROTOCOL} ${COMMUNITY_SERVICE} ${IDENTITY_SERVICE} ${WORKSHOP_SERVICE} ${CHATBOT_SERVICE} ${MAILHOG_WEB_SERVICE}' < $NGINX_TEMPLATE > /etc/nginx/conf.d/default.conf
openresty
exec "$@"