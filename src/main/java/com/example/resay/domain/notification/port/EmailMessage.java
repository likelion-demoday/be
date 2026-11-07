package com.example.resay.domain.notification.port;

import java.util.Map;

// 보낼 메일 한 통. html을 못 여는 메일 앱을 위해 text도 같이 보낸다.
// idempotencyKey가 같으면 메일 서비스가 한 번만 보낸다 (재시도로 두 번 나가지 않게)
public record EmailMessage(
        String to,
        String subject,
        String html,
        String text,
        String idempotencyKey,
        Map<String, String> headers
) {

    public EmailMessage {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
