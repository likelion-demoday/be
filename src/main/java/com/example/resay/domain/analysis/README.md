# Analysis

1:1 전사 데이터를 바탕으로 관계·상황별 대화 분석을 수행합니다.

분석 작업의 상태 관리, AI 분석 실행, 결과 저장을 담당합니다.

## 처리 흐름

```text
AnalysisSourceReader
  -> 정량 지표 계산 + 정성 모델 분석
  -> AnalysisReportAssembler
  -> 분석 상태와 보고서 저장
  -> 보고서 조회
```

정량 지표와 정성 분석은 `analysisTaskExecutor`에서 동시에 실행합니다. 저장되는 보고서는
`recordingInfo`, `quantitativeAnalysis`, `qualitativeAnalysis`로 구성됩니다.

## 로컬 목업 실행

B 전사 도메인이 연결되기 전까지 `local`, `mock` 프로필에서는 클래스패스의 목업 전사 데이터를
`MockAnalysisSourceReader`가 공급합니다.

- `POST /api/v1/mock/analyses`: 시나리오를 선택해 목업 보고서 생성
- `GET /api/v1/mock/analyses/{recordingId}`: 생성된 목업 보고서 조회

기본값은 비용이 발생하지 않는 목업 정성 모델입니다. 실제 LINER 호출을 확인할 때는
`ANALYSIS_MOCK_MODEL_ENABLED=false`와 `LINER_API_KEY`를 설정합니다. 목업 컨트롤러와 데이터
공급자는 `local`, `mock` 프로필에서만 등록됩니다.

MySQL 없이 IntelliJ에서 전체 애플리케이션을 실행할 때는 `mock` 프로필을 사용합니다. 이
프로필은 개발용 H2 인메모리 DB를 사용하므로 앱을 종료하면 가입 정보와 보고서가 사라집니다.
