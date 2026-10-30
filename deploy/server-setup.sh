#!/bin/bash
# 서버 기본 설정: 시간대, 스왑 2GB, Docker 설치.
#
# deploy/lightsail.yml 로 인스턴스를 만들면 첫 부팅 때 같은 내용이 자동 실행되므로 보통은 실행할 필요가 없다.
# 콘솔에서 직접 만든 인스턴스이거나 자동 설정이 실패했을 때만 실행한다. 여러 번 실행해도 안전하다.
#   sudo bash deploy/server-setup.sh
#
# 주의: 이 파일을 고치면 deploy/lightsail.yml 의 UserData 도 같이 고친다 (내용이 같아야 한다).
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "sudo로 실행해 주세요: sudo bash deploy/server-setup.sh"
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
# 자동 업데이트가 apt 잠금을 잡고 있을 수 있어 기다린다
APT="apt-get -o DPkg::Lock::Timeout=300"
TARGET_USER="${SUDO_USER:-ubuntu}"

echo "==> 시간대: Asia/Seoul"
timedatectl set-timezone Asia/Seoul

echo "==> 스왑 2GB (2GB 메모리에 앱, DB, 이미지 빌드를 같이 돌리기 위해 필요)"
if ! swapon --show | grep -q '/swapfile'; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
fi
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
# 메모리가 남아 있을 때는 스왑을 최대한 쓰지 않도록 한다
echo 'vm.swappiness=10' > /etc/sysctl.d/99-resay.conf
sysctl -w vm.swappiness=10 > /dev/null

echo "==> 기본 패키지"
$APT update
$APT install -y ca-certificates curl git gnupg

echo "==> Docker (공식 저장소)"
# 받은 서명 키가 Docker의 공식 키인지 지문으로 확인한 뒤 등록한다
# (grep -q는 일치 즉시 종료해서 pipefail과 함께 쓰면 드물게 실패할 수 있어 쓰지 않는다)
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
gpg --show-keys --with-colons /etc/apt/keyrings/docker.asc \
  | grep '^fpr:::::::::9DC858229FC7DD38854AE2D88D81803C0EBFCD88:' > /dev/null
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
$APT update
$APT install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 컨테이너 로그가 디스크를 채우지 않도록 전역 상한을 둔다
install -m 0755 -d /etc/docker
cat > /etc/docker/daemon.json <<'DAEMON_JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
DAEMON_JSON
systemctl enable docker
systemctl restart docker
usermod -aG docker "$TARGET_USER"

touch /var/log/resay-bootstrap-done
echo
echo "완료. 로그아웃 후 다시 접속한 뒤 'docker ps'가 sudo 없이 되는지 확인하세요."
free -h
