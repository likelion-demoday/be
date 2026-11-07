package com.example.resay.global.infrastructure.mail;

import com.example.resay.domain.notification.port.EmailMessage;
import com.example.resay.domain.notification.port.EmailSendException;
import com.example.resay.domain.notification.port.EmailSendResult;
import com.example.resay.domain.notification.port.EmailSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class ResendEmailSender implements EmailSender {

    private static final String EMAILS_PATH = "/emails";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final ResendProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ResendEmailSender(
            ResendProperties properties,
            @Qualifier("resendRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        if (!properties.isConfigured()) {
            // 받는 사람 주소는 로그에 남기지 않는다
            log.info("메일 발송 설정이 없어 보내지 않았습니다. subject={}", message.subject());
            return EmailSendResult.notConfigured();
        }

        ResendEmailRequest request = new ResendEmailRequest(
                properties.from(),
                List.of(message.to()),
                message.subject(),
                message.html(),
                message.text(),
                message.headers()
        );
        ResendEmailResponse response;
        try {
            response = restClient.post()
                    .uri(EMAILS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .headers(headers -> {
                        if (message.idempotencyKey() != null) {
                            headers.set(IDEMPOTENCY_KEY_HEADER, scopedIdempotencyKey(message.idempotencyKey()));
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ResendEmailResponse.class);
        } catch (RestClientResponseException exception) {
            throw new EmailSendException(
                    "HTTP_" + exception.getStatusCode().value() + " " + errorName(exception), exception);
        } catch (RestClientException exception) {
            throw new EmailSendException("transport_error", exception);
        }
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new EmailSendException("invalid_response", null);
        }
        return EmailSendResult.sent(response.id());
    }

    // 로컬과 운영이 같은 Resend 계정을 쓰면 녹음 ID 같은 값이 겹쳐, 로컬에서 먼저 쓴 키 때문에 운영 메일이 나가지 않을 수 있다.
    // 키마다 다른 접두어(API 키 해시 앞부분)를 붙여 환경끼리 겹치지 않게 한다
    private String scopedIdempotencyKey(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(properties.apiKey().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4) + "-" + key;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String errorName(RestClientResponseException exception) {
        try {
            ResendErrorResponse error = objectMapper.readValue(
                    exception.getResponseBodyAsString(), ResendErrorResponse.class);
            return error != null && error.name() != null ? error.name() : "unknown";
        } catch (RuntimeException parseException) {
            return "unknown";
        }
    }
}
