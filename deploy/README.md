# 배포 가이드

AWS Lightsail 한 대에 **앱 + MySQL + Caddy(HTTPS)** 를 Docker Compose로 올린다.

```text
인터넷 ──443──▶ Caddy (HTTPS 자동 발급·갱신) ──▶ app:8080 (Spring) ──▶ mysql:3306
                                                    │
                                                    └─ /app/storage (음성 파일, 볼륨)
```

| 항목 | 값 |
| --- | --- |
| 서버 | Lightsail `$12` 플랜 (2vCPU / 2GB / SSD 60GB), 서울, Ubuntu 24.04 LTS |
| 백엔드 주소 | `https://api.resay.site` |
| 운영 기간 | 10/1 ~ 11/21 데모데이 |

---

## 처음 한 번: 서버 만들기

> **1~3단계(인스턴스 · 고정 IP · 방화벽)는 CloudFormation 템플릿 `deploy/lightsail.yml`로 만든다.**
> 첫 부팅 때 스왑 2GB · Docker 설치까지 자동으로 끝나므로 5단계의 `server-setup.sh`도 실행할 필요가 없다.
>
> ```bash
> # Windows에서는 한글 주석 때문에 인코딩 지정이 필요하다
> export AWS_CLI_FILE_ENCODING=UTF-8
> aws cloudformation deploy --stack-name resay-api --template-file deploy/lightsail.yml --region ap-northeast-2
> aws cloudformation describe-stacks --stack-name resay-api --query 'Stacks[0].Outputs'   # 고정 IP 확인
> ```
>
> 현재 운영 서버: 스택 `resay-api`, 고정 IP `13.125.20.135` (2026-10-01 생성)
> 스택을 지워도 인스턴스와 고정 IP는 남도록(`DeletionPolicy: Retain`) 해두었다. 아래 1~3단계는 콘솔로 직접 만들 때의 참고용이다.

### 1. Lightsail 인스턴스 생성

1. Lightsail 콘솔 → **인스턴스 생성**
2. 리전: **서울 (ap-northeast-2)**
3. 플랫폼: **Linux/Unix**, 블루프린트: **OS 전용 → Ubuntu 24.04 LTS**
4. SSH 키: 새로 만들거나 기본 키 사용 → **키 파일은 안전한 곳에 보관** (분실하면 접속 불가)
5. 플랜: **$12 (2GB RAM, 2vCPU, 60GB SSD)**
6. 이름: `resay-api`

### 2. 고정 IP 연결

**네트워킹 → 고정 IP 생성** → `resay-api`에 연결

> 인스턴스에 붙어 있으면 무료. **인스턴스를 삭제할 때 고정 IP도 같이 해제해야** 과금되지 않는다.

### 3. 방화벽

인스턴스 → **네트워킹 → IPv4 방화벽**

| 포트 | 용도 | 열기 |
| --- | --- | --- |
| 22 | SSH | ✅ (기본) |
| 80 | HTTP (인증서 발급·HTTPS 리다이렉트) | ✅ **추가** |
| 443 | HTTPS | ✅ **추가** |
| 8080, 3306 | 앱, DB | ❌ **절대 열지 않는다** (Caddy만 외부에 노출) |

### 4. 도메인 연결 (가비아)

가비아 → **My가비아 → 도메인 관리 → `resay.site` → DNS 설정**

| 타입 | 호스트 | 값 |
| --- | --- | --- |
| A | `api` | 고정 IP |

> 반영까지 몇 분~최대 몇 시간. 로컬에서 `nslookup api.resay.site`로 고정 IP가 나오면 된다.
> **DNS가 반영되기 전에 서버를 띄우면 HTTPS 인증서 발급이 실패한다.** (6단계 전에 확인)

### 5. 서버 기본 설정

Lightsail 콘솔의 **SSH로 연결** 버튼 또는 로컬 터미널에서 접속한다.

```bash
# 레포가 private이라 GitHub 인증이 필요하다. 가장 간단한 방법: gh 로그인
sudo apt-get update && sudo apt-get install -y gh
gh auth login          # GitHub.com → HTTPS → 브라우저 또는 토큰

git clone https://github.com/likelion-demoday/be.git
cd be
sudo bash deploy/server-setup.sh   # 시간대, 스왑 2GB, Docker 설치
exit                               # 다시 접속해야 docker 권한이 적용된다
```

다시 접속한 뒤 `docker ps`가 sudo 없이 되는지 확인.

### 6. 환경변수 작성

```bash
cd ~/be
cp deploy/.env.prod.example deploy/.env.prod
chmod 600 deploy/.env.prod
nano deploy/.env.prod
```

비밀번호·JWT 키는 `openssl rand -base64 48`로 만든다. **로컬에서 쓰던 값을 재사용하지 않는다.**

### 7. 배포

```bash
./deploy/deploy.sh
```

처음에는 이미지 빌드 때문에 5분 정도 걸린다.

### 8. 확인

```bash
curl https://api.resay.site/actuator/health      # {"status":"UP"}
```

- 브라우저로 `https://api.resay.site/swagger-ui.html` 열리는지 (테스트 기간에는 `SWAGGER_ENABLED=true`)
- 회원가입 → 로그인이 되는지

### 9. 정기 작업 등록

```bash
chmod +x deploy/*.sh
crontab -e
```

```cron
# 30분마다 디스크 사용량 확인 (80% 넘으면 경고)
*/30 * * * * /home/ubuntu/be/deploy/check-disk.sh >> /home/ubuntu/disk-check.log 2>&1
# 매일 새벽 4시 DB 백업 (7일치 보관)
0 4 * * * /home/ubuntu/be/deploy/backup-db.sh >> /home/ubuntu/backup.log 2>&1
```

Lightsail **자동 스냅샷**도 켠다 (인스턴스 → 스냅샷 → 자동 스냅샷). DB 덤프는 같은 서버에 저장되므로, 서버가 통째로 망가지는 경우는 스냅샷으로 복구한다.

### 10. 외부 연동 설정

- **프론트에 주소 공유**: `https://api.resay.site`
- **카카오 콘솔**: 운영 Redirect URI 추가 (프론트 운영 도메인 기준) → `.env.prod`의 `KAKAO_ALLOWED_REDIRECT_URIS`와 똑같이
- **CORS**: `.env.prod`의 `CORS_ALLOWED_ORIGINS`에 프론트 운영 주소

---

## 평소 작업

| 할 일 | 명령 |
| --- | --- |
| 재배포 | `./deploy/deploy.sh` (런칭 후에는 `./deploy/deploy.sh main`) |
| 앱 로그 | `docker compose --env-file deploy/.env.prod -f docker-compose.prod.yml logs -f --tail 200 app` |
| 상태 | `docker compose --env-file deploy/.env.prod -f docker-compose.prod.yml ps` |
| 메모리 | `free -h` / `docker stats --no-stream` |
| 디스크 | `df -h /` |
| 환경변수 변경 반영 | `.env.prod` 수정 후 `./deploy/deploy.sh` |

### 롤백

```bash
git log --oneline -10               # 되돌릴 커밋 확인
git checkout <커밋>                  # 해당 시점으로
docker compose --env-file deploy/.env.prod -f docker-compose.prod.yml up -d --build
```

문제가 해결되면 다시 `./deploy/deploy.sh`로 브랜치 최신 상태로 돌아온다.

---

## 런칭 직전 (10월 말)

- [ ] Flyway 도입 → `.env.prod`에서 `SPRING_JPA_HIBERNATE_DDL_AUTO=update` 줄 삭제
- [ ] **운영 DB 초기화** (테스트 가입자·결제가 최종 지표에 섞이지 않도록)
- [ ] `.env.prod`에서 `SWAGGER_ENABLED=true` 줄 삭제
- [ ] DB 백업 cron 동작 확인
- [ ] 이후 배포는 `main` 브랜치로

## 데모데이(11/21) 이후

- [ ] 필요한 데이터(결제·지표) 백업 다운로드
- [ ] **인스턴스 삭제** — Lightsail은 **정지해도 과금**된다. 삭제해야 멈춘다
- [ ] 고정 IP 해제 (인스턴스 없이 남아 있으면 과금)
- [ ] 스냅샷은 남겨두면 나중에 되살릴 수 있다 (월 수백 원)

---

## 문제 해결

| 증상 | 확인할 것 |
| --- | --- |
| HTTPS 인증서 발급 실패 | `nslookup api.resay.site`가 고정 IP인지, 방화벽 80·443이 열렸는지, `docker compose ... logs caddy` |
| 앱이 안 뜸 (`deploy.sh`가 실패) | 로그의 `APPLICATION FAILED TO START` 아래 이유 확인. 대부분 `.env.prod` 누락 (`JWT_SECRET`, `CORS_ALLOWED_ORIGINS` 등은 비어 있으면 일부러 부팅을 막음) |
| 테이블이 없다는 오류 | 런칭 전이면 `.env.prod`에 `SPRING_JPA_HIBERNATE_DDL_AUTO=update`가 있는지 |
| 빌드 중 서버가 멈춤 | 메모리 부족. `free -h`로 스왑이 켜져 있는지 확인 |
| 프론트에서 CORS 오류 | `CORS_ALLOWED_ORIGINS`에 프론트 주소가 **프로토콜까지 정확히** 들어갔는지 (`https://resay.site`) |
| 카카오 로그인 `AUTH400_1` | `KAKAO_ALLOWED_REDIRECT_URIS`와 카카오 콘솔·프론트가 쓰는 주소가 글자 하나까지 같은지 |
| 업로드가 413 | Caddyfile `max_size`와 스프링 multipart 설정(210MB) 확인 |
