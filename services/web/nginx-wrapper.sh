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

# Location of the (uncommitted) TLS private key
export TLS_KEY="${TLS_KEY:-/app/certs/server.key}"

# if TLS_ENABLED is true or 1 then use https, otherwise use http
if [ "$TLS_ENABLED" = "true" ] || [ "$TLS_ENABLED" = "1" ]; then
    export HTTP_PROTOCOL=https
    export NGINX_TEMPLATE=/etc/nginx/conf.d/default.ssl.conf.template
    # The private key is not stored in the image/repo; it must be mounted
    # (see services/gencert.sh for local development).
    if [ ! -f "$TLS_KEY" ]; then
        echo "TLS is ENABLED but private key $TLS_KEY is missing. Mount the private key (secret) there, set TLS_KEY, or set TLS_ENABLED=false." >&2
        exit 1
    fi
else
    export HTTP_PROTOCOL=http
    export NGINX_TEMPLATE=/etc/nginx/conf.d/default.conf.template
    # Without a mounted private key the optional HTTPS server block cannot
    # start, so drop it and serve plain HTTP only.
    if [ ! -f "$TLS_KEY" ]; then
        awk 'BEGIN{skip=0;done=0} !done && /^server \{/ {skip=1} !skip {print} skip && /^\}/ {skip=0;done=1}' "$NGINX_TEMPLATE" > /tmp/default.nohttps.conf.template
        export NGINX_TEMPLATE=/tmp/default.nohttps.conf.template
    fi
fi
ls -al /app/certs
env
envsubst '${HTTP_PROTOCOL} ${COMMUNITY_SERVICE} ${IDENTITY_SERVICE} ${WORKSHOP_SERVICE} ${CHATBOT_SERVICE} ${MAILHOG_WEB_SERVICE} ${TLS_KEY}' < $NGINX_TEMPLATE > /etc/nginx/conf.d/default.conf
openresty
exec "$@"