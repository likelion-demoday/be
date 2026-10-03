# LINER 분석 평가 데이터

개인정보가 없는 역할극 대화를 이용해 전사 정확도에 따른 분석 결과 차이를 확인합니다.

사람이 교정한 기준 전사본은 실행용 목업과 품질 평가가 같은 데이터를 사용하도록
`src/main/resources/mock/analysis`에 둡니다. 각 시나리오 폴더는 다음 비교 파일을 포함합니다.

- `noisy.json`: 일부 전사 오류를 의도적으로 넣은 비교 전사본
- `evaluation.json`: 예상 주제, 근거 후보, 금지 결론과 오류 세그먼트

기준 전사본과 `noisy.json`은 세그먼트 ID, 화자 역할, 시간 정보가 같고 `content`만 다릅니다.

`noisy.json` 전체에는 문장부호와 띄어쓰기 오류가 포함됩니다. `notableCorruptedSegmentIds`는 그중 단어 치환이나 누락처럼 분석 결과에 영향을 줄 가능성이 있는 세그먼트만 표시합니다.

기준 전사본과 오류 전사본을 같은 프롬프트로 각각 분석한 뒤 다음 항목을 비교합니다.

1. 주요 주제 유지 여부
2. 근거 세그먼트의 타당성
3. 전사 오류에 근거한 허위 결론 생성 여부
4. 화자별 대화 방식 분류 변화
5. 시나리오에 맞지 않는 결론 생성 여부

각 대화는 약 10~11분, 57~60개 발화로 구성된 품질 비교용 데이터입니다. 20~30분 입력의 지연 시간과 장문 분석 품질은 별도 데이터로 검증해야 합니다.

## 실제 LINER 평가 실행

실제 호출은 시나리오와 전사 종류를 명시했을 때 한 건만 실행됩니다.

PowerShell에서 다음 환경변수를 설정합니다.

```powershell
$env:LINER_API_KEY="발급받은 키"
$env:LINER_QUALITY_SCENARIO="FRIEND_DAILY"
$env:LINER_QUALITY_VARIANT="CORRECTED"
```

지원하는 시나리오는 `FRIEND_DAILY`, `COUPLE_DAILY`, `COUPLE_CONFLICT`, `PARENT_CHILD_CONFLICT`이고 전사 종류는 `CORRECTED`, `NOISY`입니다.

```powershell
.\gradlew.bat test --tests "com.example.resay.global.infrastructure.liner.LinerAnalysisQualityTest" --rerun-tasks
```

평가 결과와 최종 보고서는 `build/reports/liner-quality` 아래의 JSON 파일로 생성됩니다. 교정본과 노이즈본을 각각 실행한 뒤 예상 주제, 실제 타임라인, 관찰 카테고리, 근거 발화 중첩, 금지 결론 탐지 결과를 비교합니다.
