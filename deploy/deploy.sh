#!/usr/bin/env bash
# 최신 코드를 받아 이미지를 다시 빌드하고 컨테이너를 교체한다.
#   ./deploy/deploy.sh            # develop 배포 (런칭 전 테스트 기간)
#   ./deploy/deploy.sh main       # main 배포 (런칭 후)
set -euo pipefail

BRANCH="${1:-develop}"
cd "$(dirname "$0")/.."

COMPOSE=(docker compose --env-file deploy/.env.prod -f docker-compose.prod.yml)

if [ ! -f deploy/.env.prod ]; then
  echo "deploy/.env.prod 가 없습니다. deploy/.env.prod.example 을 복사해서 값을 채워 주세요."
  exit 1
fi

echo "==> $BRANCH 브랜치 최신 코드 받기"
git fetch origin "$BRANCH"
git checkout "$BRANCH"
git pull --ff-only origin "$BRANCH"
echo "    배포 커밋: $(git log --oneline -1)"

echo "==> 이미지 빌드 후 컨테이너 교체"
"${COMPOSE[@]}" up -d --build
docker image prune -f >/dev/null

echo "==> 앱이 뜰 때까지 대기 (최대 3분)"
for _ in $(seq 1 36); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$("${COMPOSE[@]}" ps -q app)" 2>/dev/null || echo starting)"
  if [ "$status" = "healthy" ]; then
    echo "    앱 정상 기동"
    "${COMPOSE[@]}" ps
    exit 0
  fi
  sleep 5
done

echo "앱이 healthy 상태가 되지 않았습니다. 최근 로그:"
"${COMPOSE[@]}" logs --tail 100 app
exit 1
