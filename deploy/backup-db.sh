#!/usr/bin/env bash
# MySQL 전체를 덤프해 서버 홈 디렉터리에 보관한다. 최근 7일치만 남긴다.
# 결제 내역이 대회 평가 증빙이라, 런칭(10/31) 이후에는 반드시 켜 둔다.
#
# cron 등록 (매일 새벽 4시):
#   0 4 * * * /home/ubuntu/resay/deploy/backup-db.sh >> /home/ubuntu/backup.log 2>&1
#
# 복구 방법은 deploy/README.md 의 "평소 작업" 참고
set -euo pipefail
cd "$(dirname "$0")/.."
# 백업 파일은 만든 사람만 읽을 수 있게 한다
umask 077

# 백업이 실패해도 아무도 모르면 안 되므로 웹훅으로 알린다 (성공 시에는 보내지 않는다)
on_exit() {
  local status=$?
  rm -f "${PARTIAL:-}"
  [ "$status" -eq 0 ] && return
  local message="[resay] DB 백업 실패 (종료 코드 $status). 서버의 ~/backup.log 를 확인해 주세요."
  echo "$(date '+%F %T') $message"
  # 비밀값이 든 파일 전체를 읽지 않고 웹훅 주소 한 줄만 꺼낸다
  local url
  url="$(grep -E '^BACKUP_ALERT_WEBHOOK_URL=' deploy/.env.prod 2>/dev/null | cut -d= -f2- || true)"
  if [ -n "$url" ]; then
    curl -fsS --max-time 10 -H "Content-Type: application/json" \
      -d "{\"content\": \"$message\"}" "$url" >/dev/null || true
  fi
}
trap on_exit EXIT

BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

FILE="$BACKUP_DIR/resay-$(date '+%Y%m%d-%H%M').sql.gz"
# 덤프가 중간에 실패하면 반쪽짜리 파일이 백업처럼 남지 않게, 다 끝난 뒤에 이름을 바꾼다
PARTIAL="$FILE.partial"

# root 비밀번호는 MySQL 컨테이너가 이미 갖고 있는 환경변수를 컨테이너 안에서 꺼내 쓴다.
# (-e MYSQL_PWD=값 처럼 명령 인자로 넘기면 서버의 프로세스 목록에 그대로 보인다)
docker compose --env-file deploy/.env.prod -f docker-compose.prod.yml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -u root --single-transaction --routines --triggers resay' \
  < /dev/null | gzip > "$PARTIAL"
mv "$PARTIAL" "$FILE"

find "$BACKUP_DIR" -name 'resay-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') 백업 완료: $FILE ($(du -h "$FILE" | cut -f1))"
