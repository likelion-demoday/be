#!/usr/bin/env bash
# 디스크 사용량이 기준을 넘으면 로그를 남기고, 웹훅이 설정돼 있으면 알림을 보낸다.
# 디스크가 가득 차면 MySQL이 손상될 수 있어서 미리 알아야 한다.
#
# cron 등록 (30분마다):
#   */30 * * * * /home/ubuntu/resay/deploy/check-disk.sh >> /home/ubuntu/disk-check.log 2>&1
set -euo pipefail
cd "$(dirname "$0")/.."

THRESHOLD="${DISK_ALERT_PERCENT:-80}"
USAGE="$(df --output=pcent / | tail -1 | tr -dc '0-9')"

if [ "$USAGE" -lt "$THRESHOLD" ]; then
  exit 0
fi

MESSAGE="[resay] 서버 디스크 사용량 ${USAGE}% (기준 ${THRESHOLD}%). 음성 파일 정리 또는 디스크 추가가 필요합니다."
echo "$(date '+%F %T') $MESSAGE"

# 비밀값이 든 파일 전체를 읽지 않고 웹훅 주소 한 줄만 꺼낸다
WEBHOOK_URL="$(grep -E '^DISK_ALERT_WEBHOOK_URL=' deploy/.env.prod 2>/dev/null | cut -d= -f2- || true)"
if [ -n "$WEBHOOK_URL" ]; then
  curl -fsS -H "Content-Type: application/json" \
    -d "{\"content\": \"$MESSAGE\"}" "$WEBHOOK_URL" >/dev/null || true
fi
