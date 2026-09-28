#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"

if [ ! -f .env ]; then
  echo "ERROR: BE/EC2_/.env 파일이 필요합니다." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

domain=${TLS_DOMAIN:-j15a103.p.ssafy.io}
email=${CERTBOT_EMAIL:-}

if [ -z "$email" ]; then
  echo "ERROR: .env에 CERTBOT_EMAIL을 설정하세요." >&2
  exit 1
fi

echo "Starting HTTP bootstrap server for ${domain}..."
docker compose up -d --build nginx

echo "Issuing a Let's Encrypt certificate for ${domain}..."
docker compose --profile tls run --rm certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --domain "$domain" \
  --email "$email" \
  --agree-tos \
  --no-eff-email \
  --non-interactive \
  --keep-until-expiring

echo "Restarting Nginx with HTTPS enabled..."
docker compose up -d --force-recreate nginx

echo "HTTPS enabled: https://${domain}"
