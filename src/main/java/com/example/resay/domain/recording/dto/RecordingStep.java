package com.example.resay.domain.recording.dto;

// 프론트가 보여줄 화면 단계 (녹음 상태와 전사 진행 상황을 합쳐서 정한다)
public enum RecordingStep {

    // 업로드만 됨 → 대화·관계유형 선택 화면
    UPLOADED,
    // 유형 선택 완료 → 결제 화면
    TYPE_SELECTED,
    // 결제 완료 후 전사 중 → 대기(닉네임 입력) 화면
    TRANSCRIBING,
    // 전사 완료 → 화자 선택 화면
    SPEAKER_SELECTION_REQUIRED,
    // 분석 중 → 분석 대기 화면
    ANALYZING,
    // 분석 완료 → 보고서
    COMPLETED,
    // 실패 → failureReason으로 안내 문구 선택
    FAILED
}
