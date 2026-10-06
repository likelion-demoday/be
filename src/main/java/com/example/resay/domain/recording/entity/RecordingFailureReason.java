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
    // 두 명은 검출됐지만 분석하기에 발화가 부족함 (분석 쪽에서 판단)
    INSUFFICIENT_SPEAKER_DATA,
    // 분석 처리 중 오류
    ANALYSIS_FAILED
}
