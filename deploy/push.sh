#!/usr/bin/env bash
# 로컬(배포 담당자 PC)에서 실행한다.
# 원격 저장소의 지정한 커밋을 깨끗한 폴더에 꺼내 테스트·빌드한 뒤, JAR과 배포 파일만 서버로 보내 재기동한다.
#
#   ./deploy/push.sh              # origin/develop 최신 (런칭 전)
#   ./deploy/push.sh main         # origin/main 최신 (런칭 후)
#   ./deploy/push.sh 1a2b3c4      # 특정 커밋 (롤백)
#
# 서버는 레포에 접근하지 않는다. 그래서 서버에 GitHub 자격 증명도 소스 코드도 두지 않는다.
# 작업 폴더가 아니라 "원격에 올라간 커밋"을 빌드하므로, 커밋하지 않은 수정이나
# 로컬 비밀값 파일(application-local.yml)이 배포물에 섞이지 않는다.
#
# 필요한 것: 서버 SSH 키, JDK 17. 접속 정보는 환경변수로 바꿀 수 있다.
set -euo pipefail

REF="${1:-develop}"
DEPLOY_HOST="${DEPLOY_HOST:-ubuntu@13.125.20.135}"
DEPLOY_KEY="${DEPLOY_KEY:-$HOME/.ssh/lightsail-resay-api.pem}"
REMOTE_DIR="${REMOTE_DIR:-resay}"
# 처음 보는 호스트 키는 받지 않는다. 서버 호스트 키는 미리 known_hosts에 등록해 둔다 (deploy/README.md)
SSH=(ssh -i "$DEPLOY_KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o BatchMode=yes -o ServerAliveInterval=30 "$DEPLOY_HOST")

cd "$(dirname "$0")/.."

echo "==> 배포할 커밋 확인"
git fetch --quiet origin
REV="$(git rev-parse --verify --quiet "origin/$REF^{commit}" || git rev-parse --verify "$REF^{commit}")"
echo "    $(git log -1 --format='%h %s (%an, %ad)' --date=format:'%m/%d %H:%M' "$REV")"

WORK="$(mktemp -d)"
KEEP_WORK=false
trap '[ "$KEEP_WORK" = true ] || rm -rf "$WORK" 2>/dev/null || true' EXIT

echo "==> 해당 커밋을 깨끗한 폴더에 꺼내 테스트 · 빌드 (1~2분)"
# 줄바꿈 변환 없이 저장소에 있는 그대로 꺼낸다 (Windows에서 CRLF로 바뀌면 서버에서 스크립트가 실행되지 않는다)
git -c core.autocrlf=false archive --format=tar "$REV" | tar -x -C "$WORK"
# 테스트 로그는 파일로 받고 실패했을 때만 보여준다.
# (테스트용 H2 DB를 정리하는 drop table 로그가 배포 화면에 섞여 운영 DB 작업처럼 보이지 않게)
if ! (cd "$WORK" && ./gradlew --no-daemon --console=plain -q clean build) > "$WORK/build.log" 2>&1; then
  grep -E ' FAILED$' "$WORK/build.log" | head -n 20 || true
  tail -n 30 "$WORK/build.log"
  KEEP_WORK=true
  echo "테스트 또는 빌드가 실패했습니다. 배포를 중단합니다. (전체 로그: $WORK/build.log)"
  exit 1
fi

JAR="$WORK/build/libs/app.jar"
[ -f "$JAR" ] || { echo "빌드 결과물이 없습니다: $JAR"; exit 1; }
if unzip -l "$JAR" | grep -qE 'application-local\.ya?ml$'; then
  echo "JAR에 로컬 설정 파일이 들어 있습니다. 배포를 중단합니다."
  exit 1
fi
echo "    빌드 완료: $(du -h "$JAR" | cut -f1)"

echo "==> 서버로 전송"
"${SSH[@]}" "mkdir -p ~/$REMOTE_DIR"
git -c core.autocrlf=false archive --format=tar "$REV" Dockerfile .dockerignore docker-compose.prod.yml deploy \
  | "${SSH[@]}" "tar -x -C ~/$REMOTE_DIR && chmod +x ~/$REMOTE_DIR/deploy/*.sh"
# 전송이 끝난 뒤에 이름을 바꿔서, 중간에 끊겨도 반쯤 올라간 JAR로 배포되지 않게 한다
"${SSH[@]}" "cat > ~/$REMOTE_DIR/app.jar.uploading && mv ~/$REMOTE_DIR/app.jar.uploading ~/$REMOTE_DIR/app.jar && echo '$REV' > ~/$REMOTE_DIR/REVISION" < "$JAR"

echo "==> 서버에서 재기동"
"${SSH[@]}" "cd ~/$REMOTE_DIR && ./deploy/deploy.sh"
