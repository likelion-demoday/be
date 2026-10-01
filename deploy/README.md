# 배포 가이드

AWS Lightsail 한 대에 **앱 + MySQL + Caddy(HTTPS)** 를 Docker Compose로 올린다.

**배포는 로컬에서 `./deploy/push.sh` 한 번으로 한다.** 원격 저장소의 커밋을 로컬에서 테스트·빌드하고 JAR만 서버로 보낸다.
서버는 레포에 접근하지 않는다 (조직 정책으로 deploy key가 막혀 있고, 서버에 GitHub 자격 증명이나 소스를 두지 않기 위해서다).

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
> 스택을 만든 뒤 두 가지를 확인한다.
>
> ```bash
> # 1) 첫 부팅 자동 설정이 끝났는지 (서버에서). 실패했으면 로그를 보고 sudo bash deploy/server-setup.sh 로 다시 실행
> ls /var/log/resay-bootstrap-done && tail -3 /var/log/resay-bootstrap.log
>
> # 2) 컨테이너에서 인스턴스 메타데이터(임시 자격 증명)에 접근하지 못하게 홉 제한을 1로 (템플릿으로는 설정할 수 없다)
> aws lightsail update-instance-metadata-options --instance-name resay-api --http-tokens required --http-put-response-hop-limit 1
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

### 5. 서버 호스트 키 등록과 접속 확인 (로컬)

처음 접속할 때 호스트 키를 그냥 믿지 않고, Lightsail이 기록한 값과 대조해서 등록한다.

```bash
IP=13.125.20.135
aws lightsail get-instance-access-details --instance-name resay-api --protocol ssh   --query 'accessDetails.hostKeys[].[algorithm,publicKey]' --output text   | tr -d '
' | while read -r alg key; do echo "$IP $alg $key"; done >> ~/.ssh/known_hosts

# 기본 키 페어 내려받기 (내용을 화면에 출력하지 않는다)
aws lightsail download-default-key-pair --query privateKeyBase64 --output text | tr -d '
' > ~/.ssh/lightsail-resay-api.pem
chmod 600 ~/.ssh/lightsail-resay-api.pem

ssh -i ~/.ssh/lightsail-resay-api.pem -o IdentitiesOnly=yes ubuntu@$IP 'ls /var/log/resay-bootstrap-done && docker --version'
```

`deploy/lightsail.yml`로 만든 서버는 스왑 · Docker 설정이 이미 되어 있다. 콘솔에서 직접 만든 서버라면 `deploy/server-setup.sh`를 서버에서 `sudo bash`로 실행한다.

### 6. 환경변수 작성 (서버)

```bash
mkdir -p ~/resay/deploy && cd ~/resay
nano deploy/.env.prod        # deploy/.env.prod.example 의 항목을 채운다
chmod 600 deploy/.env.prod
```

비밀번호 · JWT 키는 `openssl rand -hex 32`처럼 서버에서 만든다. **로컬에서 쓰던 값을 재사용하지 않는다.**
`.env.prod`는 서버에만 있고, 배포할 때 덮어쓰지 않는다.

### 7. 배포 (로컬)

```bash
./deploy/push.sh             # origin/develop 최신 커밋
```

1. 원격의 해당 커밋을 임시 폴더에 꺼내 `./gradlew clean build` (테스트가 실패하면 배포하지 않는다)
2. JAR과 배포 파일(`Dockerfile`, `docker-compose.prod.yml`, `deploy/`)을 서버 `~/resay`로 전송
3. 서버에서 `deploy/deploy.sh` 실행 → 이미지 생성, 컨테이너 교체, 앱이 healthy가 될 때까지 대기

필요한 것: JDK 17(`JAVA_HOME`), 서버 SSH 키. 처음에는 이미지 내려받기 때문에 몇 분 걸린다.

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
*/30 * * * * /home/ubuntu/resay/deploy/check-disk.sh >> /home/ubuntu/disk-check.log 2>&1
# 매일 새벽 4시 DB 백업 (7일치 보관)
0 4 * * * /home/ubuntu/resay/deploy/backup-db.sh >> /home/ubuntu/backup.log 2>&1
```

Lightsail **자동 스냅샷**도 켠다 (인스턴스 → 스냅샷 → 자동 스냅샷). DB 덤프는 같은 서버에 저장되므로, 서버가 통째로 망가지는 경우는 스냅샷으로 복구한다.

### 10. 외부 연동 설정

- **프론트에 주소 공유**: `https://api.resay.site`
- **카카오 콘솔**: 운영 Redirect URI 추가 (프론트 운영 도메인 기준) → `.env.prod`의 `KAKAO_ALLOWED_REDIRECT_URIS`와 똑같이
- **CORS**: `.env.prod`의 `CORS_ALLOWED_ORIGINS`에 프론트 운영 주소

---

## 보안 설정 (2026-10-01 점검)

| 항목 | 상태 |
| --- | --- |
| 외부에 열린 포트 | 22 · 80 · 443만 (Lightsail 방화벽). 앱(8080) · DB(3306)는 컨테이너 내부 통신만 |
| SSH | 키 인증만 허용(비밀번호 로그인 꺼짐), 비밀번호가 설정된 계정 없음, root 로그인 차단 |
| OS 업데이트 | 전부 적용. 보안 업데이트는 매일 자동 설치(`unattended-upgrades`). 재부팅이 필요한 업데이트는 수동으로 재부팅 |
| 인스턴스 메타데이터 | IMDSv2 강제 + 홉 제한 1 (컨테이너에서 접근 불가 확인) |
| Docker | 공식 저장소에서 서명 키 지문을 확인하고 설치. 외부 TCP 소켓 없음. 컨테이너 로그 상한 설정 |
| 앱 컨테이너 | root가 아닌 사용자, 리눅스 권한 전부 제거(`cap_drop: ALL`), 권한 상승 차단 |
| 요청 크기 | 음성 업로드(`POST /api/v1/recordings`)만 210MB, 나머지 API는 1MB (Caddy) |
| HTTPS | Caddy가 인증서 자동 발급 · 갱신, HTTP는 HTTPS로 리다이렉트 |
| 비밀값 | `deploy/.env.prod`(권한 600)에만 두고 저장소에 올리지 않음 |

다시 점검할 때

```bash
sudo sshd -T | grep -E "^(passwordauthentication|permitrootlogin|kbdinteractiveauthentication) "   # no / without-password / no
sudo ss -tlnp                                   # 22, 80, 443 외에 0.0.0.0 으로 열린 포트가 없어야 한다
apt list --upgradable 2>/dev/null | wc -l       # 1이면 대기 중인 업데이트 없음
[ -f /var/run/reboot-required ] && echo "재부팅 필요"
```

알아둘 것

- Docker는 OS 자동 업데이트 대상이 아니다(공식 저장소라서). 필요할 때 `sudo apt-get update && sudo apt-get install --only-upgrade docker-ce docker-ce-cli containerd.io`로 올린다. 올리면 컨테이너가 잠깐 재시작된다
- SSH(22)는 전체에 열려 있다. 키 인증만 받으므로 무차별 대입은 통하지 않지만, 더 조이려면 Lightsail 방화벽에서 22번의 허용 IP를 제한한다
- 로그인 시도 횟수 제한(rate limit)은 아직 없다. 런칭 전에 추가를 검토한다

## 평소 작업

| 할 일 | 어디서 | 명령 |
| --- | --- | --- |
| 재배포 | 로컬 | `./deploy/push.sh` (런칭 후에는 `./deploy/push.sh main`) |
| 환경변수 변경 반영 | 서버 | `deploy/.env.prod` 수정 후 `cd ~/resay && ./deploy/deploy.sh` |
| 앱 로그 | 서버 | `cd ~/resay && docker compose --env-file deploy/.env.prod -f docker-compose.prod.yml logs -f --tail 200 app` |
| 상태 | 서버 | `cd ~/resay && docker compose --env-file deploy/.env.prod -f docker-compose.prod.yml ps` |
| 메모리 · 디스크 | 서버 | `free -h` / `docker stats --no-stream` / `df -h /` |
| 지금 배포된 커밋 | 서버 | `cat ~/resay/REVISION` |

앱에 새 환경변수가 필요해지면 `deploy/.env.prod`에 값을 넣고, **`docker-compose.prod.yml`의 `app.environment` 목록에도 이름을 추가**해야 앱에 전달된다.

### 롤백

```bash
git log --oneline -10 origin/develop     # 되돌릴 커밋 확인
./deploy/push.sh <커밋해시>               # 그 커밋을 다시 빌드해서 배포
```

## 런칭 직전 (10월 말)

- [ ] Flyway 도입 → `.env.prod`에서 `SPRING_JPA_HIBERNATE_DDL_AUTO=update` 줄 삭제
- [ ] **운영 DB 초기화** (테스트 가입자·결제가 최종 지표에 섞이지 않도록)
- [ ] `.env.prod`에서 `SWAGGER_ENABLED=true` 줄 삭제
- [ ] DB 백업 cron 동작 확인
- [ ] 이후 배포는 `./deploy/push.sh main`

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
| `push.sh`가 빌드에서 멈춤 | 테스트 실패. 메시지를 확인하고 고친 뒤 다시 푸시 · 배포. `JAVA_HOME`이 JDK 17을 가리키는지도 확인 |
| `push.sh`가 접속에서 멈춤 | 서버 호스트 키가 `known_hosts`에 없거나 SSH 키 경로가 다름 (5단계) |
| 프론트에서 CORS 오류 | `CORS_ALLOWED_ORIGINS`에 프론트 주소가 **프로토콜까지 정확히** 들어갔는지 (`https://resay.site`) |
| 카카오 로그인 `AUTH400_1` | `KAKAO_ALLOWED_REDIRECT_URIS`와 카카오 콘솔·프론트가 쓰는 주소가 글자 하나까지 같은지 |
| 업로드가 413 | Caddyfile `max_size`와 스프링 multipart 설정(210MB) 확인 |
