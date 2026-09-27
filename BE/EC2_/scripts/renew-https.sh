#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"

docker compose --profile tls run --rm certbot renew \
  --webroot \
  --webroot-path /var/www/certbot \
  --quiet

# 인증서 파일이 교체된 경우에도 새 인증서를 확실히 읽도록 재생성한다.
docker compose up -d --force-recreate nginx
