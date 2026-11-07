package com.example.resay.domain.notification.port;

// 메일 발송 서비스(지금은 Resend)를 감싼다. 다른 서비스로 바꿀 때 구현체만 교체한다
public interface EmailSender {

    // 발송 설정이 없으면 보내지 않고 notConfigured를 돌려준다. 거절 · 통신 실패는 EmailSendException
    EmailSendResult send(EmailMessage message);
}
