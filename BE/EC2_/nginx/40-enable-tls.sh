#!/bin/sh
set -eu

domain="${TLS_DOMAIN:-j15a103.p.ssafy.io}"
certificate="/etc/letsencrypt/live/${domain}/fullchain.pem"
private_key="/etc/letsencrypt/live/${domain}/privkey.pem"

if [ -s "$certificate" ] && [ -s "$private_key" ]; then
  sed "s/__TLS_DOMAIN__/${domain}/g" \
    /opt/ssafy-nginx/default-https.conf > /etc/nginx/conf.d/default.conf
  echo "TLS certificate found for ${domain}; HTTPS is enabled."
else
  sed "s/__TLS_DOMAIN__/${domain}/g" \
    /opt/ssafy-nginx/default-http.conf > /etc/nginx/conf.d/default.conf
  echo "TLS certificate not found for ${domain}; ACME bootstrap HTTP is enabled."
fi
