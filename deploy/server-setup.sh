#!/usr/bin/env bash
# 새 Lightsail 인스턴스(Ubuntu 24.04)에서 처음 한 번만 실행한다.
#   sudo bash deploy/server-setup.sh
# 끝나면 로그아웃했다가 다시 접속해야 docker 명령을 sudo 없이 쓸 수 있다.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "sudo로 실행해 주세요: sudo bash deploy/server-setup.sh"
  exit 1
fi

TARGET_USER="${SUDO_USER:-ubuntu}"

echo "==> 시간대: Asia/Seoul"
timedatectl set-timezone Asia/Seoul

echo "==> 스왑 2GB (2GB 메모리에 앱·DB·빌드를 같이 돌리기 위해 필요)"
if ! swapon --show | grep -q '/swapfile'; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
# 메모리가 남아 있을 때는 스왑을 최대한 쓰지 않도록 한다
sysctl -w vm.swappiness=10
grep -q '^vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=10' >> /etc/sysctl.conf

echo "==> 패키지 업데이트, git 설치"
apt-get update
apt-get install -y git curl

echo "==> Docker 설치"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
usermod -aG docker "$TARGET_USER"
systemctl enable --now docker

echo
echo "완료. 로그아웃 후 다시 접속한 뒤 'docker ps'가 sudo 없이 되는지 확인하세요."
free -h
