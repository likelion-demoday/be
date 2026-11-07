package com.example.resay.global.infrastructure.mail;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

// POST /emails 요청 본문 (https://resend.com/docs/api-reference/emails/send-email)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResendEmailRequest(
        String from,
        List<String> to,
        String subject,
        String html,
        String text,
        Map<String, String> headers
) {
}
