#!/usr/bin/env bash
# 서버에서 실행된다. 올라와 있는 app.jar 로 이미지를 만들고 컨테이너를 교체한다.
# 보통은 로컬의 deploy/push.sh 가 호출한다. 설정(.env.prod)만 바꿨을 때는 서버에서 직접 실행해도 된다.
#   ./deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE=(docker compose --env-file deploy/.env.prod -f docker-compose.prod.yml)

if [ ! -f deploy/.env.prod ]; then
  echo "deploy/.env.prod 가 없습니다. deploy/.env.prod.example 을 복사해서 값을 채워 주세요."
  exit 1
fi
if [ ! -f app.jar ]; then
  echo "app.jar 가 없습니다. 로컬에서 ./deploy/push.sh 로 배포해 주세요."
  exit 1
fi

echo "==> 배포 리비전: $(cat REVISION 2>/dev/null || echo '알 수 없음')"

echo "==> 이미지 생성 후 컨테이너 교체"
"${COMPOSE[@]}" up -d --build --remove-orphans
docker image prune -f >/dev/null

echo "==> 앱이 뜰 때까지 대기 (최대 4분)"
for _ in $(seq 1 48); do
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
