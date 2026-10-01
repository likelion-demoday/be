#!/usr/bin/env bash
# 서버에서 실행된다. 올라와 있는 app.jar 로 이미지를 만들고 컨테이너를 교체한다.
# 보통은 로컬의 deploy/push.sh 가 호출한다. 설정(.env.prod)만 바꿨을 때는 서버에서 직접 실행해도 된다.
#   ./deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE=(docker compose --env-file deploy/.env.prod -f docker-compose.prod.yml)
CADDY_CONFIG=(--config /etc/caddy/Caddyfile --adapter caddyfile)

if [ ! -f deploy/.env.prod ]; then
  echo "deploy/.env.prod 가 없습니다. deploy/.env.prod.example 을 복사해서 값을 채워 주세요."
  exit 1
fi
if [ ! -f app.jar ]; then
  echo "app.jar 가 없습니다. 로컬에서 ./deploy/push.sh 로 배포해 주세요."
  exit 1
fi

echo "==> 배포 리비전: $(cat REVISION 2>/dev/null || echo '알 수 없음')"

echo "==> Caddy 설정 검사"
# 문법이 틀린 설정으로 Caddy가 다시 뜨면 HTTPS 전체가 내려간다. 컨테이너를 건드리기 전에 임시 컨테이너로 먼저 검사한다.
# 비밀값이 든 파일 전체를 넘기지 않고 도메인 한 줄만 꺼낸다
API_DOMAIN="$(grep -E '^API_DOMAIN=' deploy/.env.prod | cut -d= -f2- || true)"
if [ -z "$API_DOMAIN" ]; then
  echo "deploy/.env.prod 에 API_DOMAIN 이 없습니다."
  exit 1
fi
if ! output="$(docker run --rm -e API_DOMAIN="$API_DOMAIN" -v "$PWD/deploy/caddy:/etc/caddy:ro" caddy:2 \
    caddy validate "${CADDY_CONFIG[@]}" 2>&1)"; then
  echo "$output" | tail -n 20
  echo "deploy/caddy/Caddyfile 에 오류가 있습니다. 배포를 중단합니다."
  exit 1
fi

echo "==> 이미지 생성 후 컨테이너 교체"
"${COMPOSE[@]}" up -d --build --remove-orphans
docker image prune -f >/dev/null

echo "==> 앱이 뜰 때까지 대기 (최대 4분)"
healthy=false
for _ in $(seq 1 48); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$("${COMPOSE[@]}" ps -q app)" 2>/dev/null || echo starting)"
  if [ "$status" = "healthy" ]; then
    healthy=true
    break
  fi
  sleep 5
done
if [ "$healthy" != true ]; then
  echo "앱이 healthy 상태가 되지 않았습니다. 최근 로그:"
  "${COMPOSE[@]}" logs --tail 100 app
  exit 1
fi
echo "    앱 정상 기동"

echo "==> Caddy 설정 반영"
# Caddy는 실행 중에 설정 파일을 스스로 다시 읽지 않는다.
# 내용이 그대로면 아무 일도 일어나지 않고, 바뀌었으면 연결을 끊지 않고 교체된다
if ! output="$("${COMPOSE[@]}" exec -T caddy caddy reload "${CADDY_CONFIG[@]}" 2>&1)"; then
  echo "$output" | tail -n 20
  echo "Caddy 설정을 반영하지 못했습니다. (이전 설정으로 계속 동작 중)"
  exit 1
fi

"${COMPOSE[@]}" ps
