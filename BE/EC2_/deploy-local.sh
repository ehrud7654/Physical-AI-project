#!/usr/bin/env bash
set -Eeuo pipefail

cd -- "$(dirname -- "$(readlink -f -- "$0")")"

DEPLOY_TAG="${1:-}"

# GitLab 파이프라인 ID만 허용
if [[ ! "$DEPLOY_TAG" =~ ^[0-9]+$ ]]; then
  echo "Invalid pipeline ID"
  exit 1
fi

export DEPLOY_TAG

# 이미지 두 개가 모두 있어야 배포 시작
sudo -n docker image inspect \
  "ssafy-ci-backend:${DEPLOY_TAG}" >/dev/null

sudo -n docker image inspect \
  "ssafy-ci-frontend:${DEPLOY_TAG}" >/dev/null

compose() {
  sudo -n env DEPLOY_TAG="$DEPLOY_TAG" docker compose \
    -f compose.yaml \
    -f compose.cd.yaml \
    "$@"
}

# 운영 컨테이너가 조회되지 않으면 잘못된 경로/프로젝트일 수 있음
for service in backend frontend nginx; do
  if [[ -z "$(compose ps -q "$service")" ]]; then
    echo "Running service not found: $service"
    echo "Check deployment directory and Compose project name."
    exit 1
  fi
done

# 설정 검증: 비밀값은 출력하지 않음
compose config --quiet

# 교체 전 이미지 정보를 기록
mkdir -p deployment-history
for service in backend frontend; do
  container_id="$(compose ps -q "$service")"
  sudo -n docker inspect \
    --format '{{.Name}} {{.Config.Image}} {{.Image}}' \
    "$container_id"
done > "deployment-history/before-${DEPLOY_TAG}-$(date +%Y%m%d-%H%M%S).txt"

echo "Deploying backend: ${DEPLOY_TAG}"
compose up -d \
  --no-deps --no-build --pull never \
  --wait --wait-timeout 300 \
  backend

echo "Deploying frontend: ${DEPLOY_TAG}"
compose up -d \
  --no-deps --no-build --pull never \
  --wait --wait-timeout 300 \
  frontend

# 교체된 컨테이너 주소를 Nginx가 다시 확인하도록 reload
compose exec -T nginx nginx -t
compose exec -T nginx nginx -s reload

compose ps
printf '%s\n' "$DEPLOY_TAG" > deployment-history/current-tag

echo "Deployment completed: ${DEPLOY_TAG}"
