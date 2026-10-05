# image

OpenAI Image API를 이용한 캐릭터 이미지 생성 구현입니다.

- `OpenAiCharacterPromptFactory`: 분석 결과를 고정된 마스코트 스타일 프롬프트로 변환
- `OpenAiImageApiClient`: 인증, HTTP 호출, 응답 및 오류 처리
- `OpenAiCharacterImageGenerator`: Base64 응답을 이미지 바이트로 변환
- `OpenAiImageProperties`: 모델, 크기, 품질, 형식, 배경, 타임아웃 설정

실제 품질 테스트는 `OPENAI_API_KEY`와 `OPENAI_CHARACTER_CASE`를 환경변수로 설정한 뒤
`OpenAiCharacterImageQualityTest`를 실행합니다. 생성 이미지는
`build/reports/openai-character-quality`에 저장됩니다.

지원하는 테스트 케이스는 다음과 같습니다.

- `FRIEND_SELF`
- `COUPLE_SELF`
- `COUPLE_CONFLICT_PARTNER`
- `PARENT`
- `CHILD`
