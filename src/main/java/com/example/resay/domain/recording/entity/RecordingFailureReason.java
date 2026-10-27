package com.example.resay.domain.recording.entity;

// 프론트가 실패 원인에 맞는 안내 문구를 고를 수 있도록 함께 저장한다
public enum RecordingFailureReason {

    // CLOVA에 전사 요청을 보내지 못함 (키 오류, 네트워크 오류 등)
    TRANSCRIPTION_REQUEST_FAILED,
    // CLOVA가 전사에 실패했다고 응답함
    TRANSCRIPTION_FAILED,
    // 두 사람의 목소리를 구분하지 못함 (화자가 한 명만 검출됨)
    SPEAKER_NOT_SEPARATED,
    // 제한 시간 안에 전사 결과가 오지 않음
    TRANSCRIPTION_TIMEOUT,
    // 전사가 끝났는데 기한 안에 화자를 고르지 않음
    SPEAKER_SELECTION_EXPIRED,
    // 두 명은 검출됐지만 분석하기에 발화가 부족함 (분석 쪽에서 판단)
    INSUFFICIENT_SPEAKER_DATA,
    // 분석 제공자의 일시적인 장애, 네트워크 오류 또는 요청 제한
    EXTERNAL_SERVICE_UNAVAILABLE,
    // 분석 응답이 완료되지 않았거나 서비스 규칙을 충족하지 못함
    INVALID_ANALYSIS_RESPONSE,
    // 분석 처리 중 내부 오류
    PROCESSING_ERROR,
    // 상세 분석 실패 사유를 저장하기 전에 생성된 레거시 데이터
    ANALYSIS_FAILED
}
