package com.example.resay.domain.transcription.entity;

public enum TranscriptionStatus {

    // 외부 전사 서비스에 요청했고 결과를 기다리는 중
    REQUESTED,
    COMPLETED,
    FAILED
}
