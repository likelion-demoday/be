# image

OpenAI Image API를 이용한 캐릭터 이미지 생성 구현입니다.

- `OpenAiCharacterPromptFactory`: 캐릭터 요약과 핵심·보조 대화 특징을 RE:SAY 3D 캐릭터 디자인 프롬프트로 변환
- `OpenAiImageApiClient`: 인증, HTTP 호출, 응답 및 오류 처리
- `OpenAiCharacterImageGenerator`: Base64 응답을 이미지 바이트로 변환
- `OpenAiImageProperties`: 모델, 크기, 품질, 형식, 배경, 타임아웃 설정

실제 품질 테스트는 `OPENAI_API_KEY`와 `OPENAI_CHARACTER_CASE`를 환경변수로 설정한 뒤
`OpenAiCharacterImageQualityTest`를 실행합니다. 생성 이미지는
`build/reports/openai-character-quality`에 저장됩니다.

결과는 카드 위에 바로 배치할 수 있는 `1024x1024` 투명 PNG입니다. 프롬프트에서
캐릭터와 직접 사용하는 소품만 남기고 배경, 바닥, 받침대와 접지 그림자를 제외하며,
캐릭터의 화면 점유율과 중심 위치를 고정합니다. 이 규격은 프론트 카드 레이아웃 계약이므로
크기, 출력 형식과 배경 설정은 환경변수로 변경하지 않습니다.

지원하는 테스트 케이스는 다음과 같습니다.

- `FRIEND_SELF`
- `COUPLE_SELF`
- `COUPLE_CONFLICT_PARTNER`
- `PARENT`
- `CHILD`
