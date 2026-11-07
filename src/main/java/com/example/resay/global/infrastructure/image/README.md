# image

OpenAI Image API를 이용한 캐릭터 이미지 생성 구현입니다.

- `LinerCharacterDesignGenerator`: 보고서의 캐릭터 특징을 구체적인 외형·의상·소품·4색 팔레트 디자인 명세로 변환
- `OpenAiCharacterPromptFactory`: 고정 렌더링 규칙과 디자인 명세를 OpenAI 이미지 프롬프트로 조립
- `OpenAiImageApiClient`: 인증, HTTP 호출, 응답 및 오류 처리
- `OpenAiCharacterImageGenerator`: Base64 응답을 이미지 바이트로 변환
- `OpenAiImageProperties`: 모델, 크기, 품질, 형식, 배경, 타임아웃 설정

실제 품질 테스트는 `LINER_API_KEY`, `OPENAI_API_KEY`, `OPENAI_CHARACTER_CASE`를 환경변수로 설정한 뒤
`OpenAiCharacterImageQualityTest`를 실행합니다. 생성 이미지는
`build/reports/openai-character-quality`에 저장됩니다.

결과는 카드 위에 바로 배치할 수 있는 `1024x1024` 투명 PNG입니다. 프롬프트에서
캐릭터와 직접 사용하는 소품만 남기고 배경, 바닥, 받침대와 접지 그림자를 제외하며,
캐릭터의 화면 점유율과 중심 위치를 고정합니다. 이 규격은 프론트 카드 레이아웃 계약이므로
크기, 출력 형식과 배경 설정은 환경변수로 변경하지 않습니다.

생성은 두 단계로 진행합니다. 먼저 LINER가 `character-design-prompt-v3`와
`character-design-result-v2` 스키마에 따라 캐릭터 형태, 실제 의복의 구조와 소재, 소품의
물성·배치, 서로 다른 네 가지 HEX 색상과 독립 실행 가능한 영문 이미지 프롬프트를 결정합니다.
그 다음 OpenAI Image API는 `character-image-prompt-v6`의 고정 렌더링 규칙에 따라 해당
명세를 이미지로 구현합니다. 이름 불일치, 색상 중복, 선택 소품·보조 의상 누락과 이미지
프롬프트의 색상 누락은 서버에서 자동 보정합니다. 응답 JSON을 해석할 수 없거나 응답이
중간에 끝난 경우에는 LINER를 한 번 더 호출하고, 재시도 후에도 핵심 명세를 복구할 수 없을
때만 이미지 작업을 실패 처리합니다. 이 실패는 완료된 대화 분석 상태에 영향을 주지 않습니다.

지원하는 테스트 케이스는 다음과 같습니다.

- `FRIEND_SELF`
- `COUPLE_SELF`
- `COUPLE_CONFLICT_PARTNER`
- `PARENT`
- `CHILD`
