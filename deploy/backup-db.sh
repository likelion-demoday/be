#!/usr/bin/env bash
# MySQL 전체를 덤프해 서버 홈 디렉터리에 보관한다. 최근 7일치만 남긴다.
# 결제 내역이 대회 평가 증빙이라, 런칭(10/31) 이후에는 반드시 켜 둔다.
#
# cron 등록 (매일 새벽 4시):
#   0 4 * * * /home/ubuntu/be/deploy/backup-db.sh >> /home/ubuntu/backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")/.."

BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${KEEP_DAYS:-7}"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

ROOT_PASSWORD="$(grep -E '^MYSQL_ROOT_PASSWORD=' deploy/.env.prod | cut -d= -f2-)"
FILE="$BACKUP_DIR/resay-$(date '+%Y%m%d-%H%M').sql.gz"

# 비밀번호가 프로세스 목록에 보이지 않도록 환경변수로 넘긴다
docker compose --env-file deploy/.env.prod -f docker-compose.prod.yml exec -T \
  -e MYSQL_PWD="$ROOT_PASSWORD" mysql \
  mysqldump -u root --single-transaction --routines --triggers resay \
  | gzip > "$FILE"
chmod 600 "$FILE"

find "$BACKUP_DIR" -name 'resay-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') 백업 완료: $FILE ($(du -h "$FILE" | cut -f1))"
