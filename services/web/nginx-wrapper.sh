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
ls -al /app/certs
env

# The TLS private key is not baked into the image. It must be supplied at
# runtime (mounted at /app/certs/server.key, see services/gencert.sh).
if [ ! -s /app/certs/server.key ]; then
    if [ "$TLS_ENABLED" = "true" ] || [ "$TLS_ENABLED" = "1" ]; then
        echo "ERROR: TLS_ENABLED is set but /app/certs/server.key is missing or empty. Mount the TLS private key at /app/certs/server.key or set TLS_ENABLED=false." >&2
        exit 1
    fi
    # TLS disabled and no key available: drop the leading HTTPS server block
    # (up to its closing brace at column 0) so nginx can start on plain HTTP.
    echo "WARNING: /app/certs/server.key not found; HTTPS listener on 443 is disabled."
    awk 'BEGIN{skip=0;done=0} !done && /^server \{/ {skip=1} skip && /^\}/ {skip=0;done=1;next} !skip {print}' "$NGINX_TEMPLATE" > /tmp/nginx.template
    NGINX_TEMPLATE=/tmp/nginx.template
fi
envsubst '${HTTP_PROTOCOL} ${COMMUNITY_SERVICE} ${IDENTITY_SERVICE} ${WORKSHOP_SERVICE} ${CHATBOT_SERVICE} ${MAILHOG_WEB_SERVICE}' < $NGINX_TEMPLATE > /etc/nginx/conf.d/default.conf
openresty
exec "$@"