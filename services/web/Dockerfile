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


# Node build
FROM node:20 AS build
RUN mkdir /app
WORKDIR /app
COPY ./*.json /app/
RUN npm install --silent
COPY ./src /app/src
COPY ./public /app/public
RUN cp ./src/config.js.template ./src/config.js
RUN NODE_OPTIONS=--max_old_space_size=4096 npm run build
RUN ls /app/build

# Main Image
FROM openresty/openresty:alpine
RUN apk add gettext curl
# React
RUN rm /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html
COPY ./nginx-wrapper.sh /etc/nginx/nginx-wrapper.sh
COPY ./nginx.conf.template /etc/nginx/conf.d/default.conf.template
COPY ./nginx.ssl.conf.template /etc/nginx/conf.d/default.ssl.conf.template
RUN mkdir -p /app
COPY ./certs /app/certs
RUN echo "daemon off;" >> /usr/local/openresty/nginx/conf/nginx.conf
EXPOSE 80
EXPOSE 443

# Run as a non-root user. The nginx binary gets the file capability to bind
# the privileged ports 80/443; writable paths (generated config, pid, temp)
# are owned by the unprivileged user.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app \
    && apk add --no-cache --virtual .setcap-deps libcap-utils \
    && setcap cap_net_bind_service=+ep /usr/local/openresty/nginx/sbin/nginx \
    && apk del .setcap-deps \
    && rm -f /usr/local/openresty/nginx/logs/access.log /usr/local/openresty/nginx/logs/error.log \
    && touch /usr/local/openresty/nginx/logs/access.log /usr/local/openresty/nginx/logs/error.log \
    && chown -R app:app /etc/nginx/conf.d /usr/local/openresty/nginx /app
USER app

CMD [ "/etc/nginx/nginx-wrapper.sh" ]
