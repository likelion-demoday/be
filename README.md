# Conversation Habit Analysis Backend

대화 음성을 업로드하면 화자를 구분해 전사하고, 대화 습관과 개선 포인트를 분석해 주는 서비스의 백엔드 저장소입니다.

## MVP 범위

- 일반 로그인 및 소셜 로그인
- 최대 30분 음성 파일 업로드
- 최대 4명 화자 분리 및 스크립트 생성
- 사용자 본인 화자 확인
- 개인·단체 대화 분석 리포트 생성
- 근거 발화 스크립트 및 원본 음성 구간 재생
- 분석 기록 조회 및 삭제
- 대화 캐릭터 생성

## 처리 흐름

```text
음성 업로드
  → CLOVA Speech 전사·화자 분리
  → 서버 정량 지표 계산
  → OpenAI 대화 습관 분석
  → 근거 발화 연결
  → 리포트 저장·조회
```

## 프로젝트 구조

도메인 중심으로 패키지를 구성합니다. 기능이 다른 도메인의 내부 구현에 직접 의존하지 않도록 합니다.

```text
src
├── main
│   ├── java
│   │   └── {base-package}
│   │       ├── domain
│   │       │   ├── auth
│   │       │   ├── user
│   │       │   ├── recording
│   │       │   ├── transcription
│   │       │   ├── analysis
│   │       │   └── report
│   │       ├── global
│   │       │   ├── apiPayload
│   │       │   ├── config
│   │       │   ├── exception
│   │       │   ├── infrastructure
│   │       │   └── security
│   │       └── Application.java
│   └── resources
│       ├── application.yml
│       └── application-local.yml.example
└── test
    └── java
        └── {base-package}
```

| Package | 역할 |
| --- | --- |
| `domain` | 도메인별 API와 비즈니스 로직 |
| `global/apiPayload` | 공통 응답 형식과 상태 코드 |
| `global/config` | 애플리케이션 공통 설정 |
| `global/exception` | 공통 예외와 예외 처리 |
| `global/infrastructure` | CLOVA, OpenAI, 스토리지 등 외부 연동 |
| `global/security` | 인증·인가와 JWT 처리 |

각 도메인의 기본 구성은 다음을 따릅니다. 필요하지 않은 패키지는 만들지 않습니다.

```text
domain/{domain}
├── controller
├── service
├── repository
├── entity
├── dto
└── code
```

## Git Convention

### Branch Strategy

| 브랜치 | 용도 |
| --- | --- |
| `main` | 배포 가능한 안정 버전 |
| `develop` | 기능 통합 및 개발 기준 브랜치 |
| `feat/#이슈번호-설명` | 기능 개발 |
| `fix/#이슈번호-설명` | 버그 수정 |
| `refactor/#이슈번호-설명` | 동작 변경 없는 리팩터링 |
| `test/#이슈번호-설명` | 테스트 추가·수정 |
| `docs/#이슈번호-설명` | 문서 수정 |
| `chore/#이슈번호-설명` | 설정, 의존성, 배포 작업 |
| `hotfix/#이슈번호-설명` | 배포 브랜치의 긴급 수정 |

예시:

```text
feat/#12-social-login
fix/#31-speaker-mapping
docs/#4-update-readme
```

규칙:

- 기능 브랜치는 최신 `develop`에서 생성합니다.
- `main`, `develop`에는 직접 push하지 않습니다.
- 한 브랜치는 하나의 이슈만 해결합니다.
- 브랜치 설명은 짧은 영문 kebab-case로 작성합니다.
- 배포는 `develop → main` Pull Request로 진행합니다.

### Commit Convention

```text
type: 작업 내용
```

| Type | 설명 |
| --- | --- |
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서 수정 |
| `chore` | 설정, 의존성, 빌드 작업 |
| `perf` | 성능 개선 |
| `init` | 프로젝트 초기 설정 |

예시:

```text
feat: 카카오 소셜 로그인 구현
fix: 화자 매핑이 뒤바뀌는 문제 수정
test: 일반 로그인 서비스 테스트 추가
```

커밋 메시지는 명령형 현재 시제로 작성하고, 마침표를 붙이지 않습니다.

### Issue Convention

- 개발을 시작하기 전에 이슈를 먼저 생성하고 담당자를 지정합니다.
- 기능은 사용자 관점의 완료 조건을 `Acceptance Criteria`에 작성합니다.
- API 변경이 있으면 Method, Path, Request, Response를 이슈에 기록합니다.
- 작업 도중 범위가 커지면 기존 이슈를 비대하게 만들지 말고 새 이슈로 분리합니다.
- 완료된 체크리스트와 테스트 결과를 확인한 후 이슈를 닫습니다.

이슈 제목 형식:

```text
[FEAT] 일반 로그인 API 구현
[BUG] 화자별 발화 시간이 중복 집계되는 문제
[TASK] CLOVA Speech 연동 환경 구성
```

### Pull Request Convention

- 기본 Base Branch는 `develop`입니다.
- PR 제목은 `[Type] 작업 내용` 형식으로 작성합니다.
- 관련 이슈는 `Closes #이슈번호`로 연결합니다.
- Reviewer를 최소 1명 지정하고 1명 이상의 Approve 후 Merge합니다.
- 리뷰 반영 여부와 테스트 결과를 반드시 작성합니다.
- 리뷰 가능한 크기를 유지하고 서로 무관한 변경을 한 PR에 넣지 않습니다.
- 기능 브랜치 → `develop` 머지는 `Squash and merge`를 기본으로 합니다.
- `develop` → `main` 배포 머지는 `Create a merge commit`을 사용합니다.
  - Squash하면 여러 팀원의 커밋이 하나로 합쳐져 `main` 기준 Contributors에서 개별 authorship이 사라집니다.

예시:

```text
[Feat] 일반 로그인 및 JWT 발급 구현
[Fix] 전사 결과 화자 매핑 오류 수정
```

## Code Convention

### Naming

- 클래스: `PascalCase`
- 메서드·변수: `camelCase`
- 상수: `UPPER_SNAKE_CASE`
- 패키지: 소문자 단수형
- 요청 DTO: `XxxRequestDto`
- 응답 DTO: `XxxResponseDto`
- 컨트롤러: `XxxController`
- 서비스: `XxxService`
- 저장소: `XxxRepository`

### API

- URL은 명사형 복수 자원을 사용합니다. 예: `/api/v1/recordings/{recordingId}`
- HTTP Method 의미를 지킵니다.
- Entity를 API 응답으로 직접 노출하지 않습니다.
- 성공·실패 응답은 프로젝트의 공통 응답 형식을 사용합니다.
- Controller에는 요청 검증과 응답 변환만 두고 비즈니스 로직은 Service에 둡니다.

### External API

- CLOVA Speech와 OpenAI 응답 객체를 도메인 계층에서 직접 사용하지 않습니다.
- 외부 API 응답은 `global/infrastructure`에서 내부 모델로 변환합니다.
- 전사 결과에는 provider, 모델, 처리 시각과 원본 응답 위치를 기록합니다.
- 동일 음성의 중복 전사를 막기 위해 파일 해시와 처리 옵션을 기준으로 캐싱합니다.
- 외부 API 호출에는 timeout, 재시도 횟수와 실패 상태를 명시합니다.

### Security

- API Key, JWT Secret, DB 비밀번호를 저장소에 커밋하지 않습니다.
- 민감값은 환경변수 또는 Secret Manager로 주입합니다.
- 실제 값이 없는 `.example` 설정 파일만 공유합니다.
- 로그에 토큰, 비밀번호, 원본 대화 내용과 개인정보를 남기지 않습니다.

## Definition of Done

다음 조건을 만족해야 작업 완료로 봅니다.

- [ ] 이슈의 Acceptance Criteria 충족
- [ ] 정상·예외 흐름 구현
- [ ] 관련 테스트 작성 및 통과
- [ ] API 변경 시 명세 갱신
- [ ] 민감 정보와 불필요한 로그가 없는지 확인
- [ ] Reviewer 1명 이상의 Approve
- [ ] `develop` 기준 충돌 및 빌드 오류 없음

