package com.example.resay.domain.notification.entity;

public enum NotificationLogStatus {
    // 보내기 직전. 보내는 중에 서버가 꺼지면 이 상태로 남고, 중복 발송을 막기 위해 다시 보내지 않는다
    PENDING,
    SENT,
    FAILED,
    // 발송 설정(API 키)이 없어 보내지 않음 (로컬 · 테스트)
    NOT_CONFIGURED
}
