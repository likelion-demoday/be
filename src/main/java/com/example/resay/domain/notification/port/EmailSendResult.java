package com.example.resay.domain.notification.port;

// sent가 false면 발송 설정(API 키)이 없어 보내지 않은 것이다 (로컬 · 테스트)
public record EmailSendResult(boolean sent, String messageId) {

    public static EmailSendResult sent(String messageId) {
        return new EmailSendResult(true, messageId);
    }

    public static EmailSendResult notConfigured() {
        return new EmailSendResult(false, null);
    }
}
