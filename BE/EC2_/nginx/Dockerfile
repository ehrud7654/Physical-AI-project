FROM nginx:stable-alpine

COPY default-http.conf /opt/ssafy-nginx/default-http.conf
COPY default-https.conf /opt/ssafy-nginx/default-https.conf
COPY proxy_params /etc/nginx/proxy_params
COPY 40-enable-tls.sh /docker-entrypoint.d/40-enable-tls.sh

RUN sed -i 's/\r$//' /docker-entrypoint.d/40-enable-tls.sh \
    && chmod +x /docker-entrypoint.d/40-enable-tls.sh
