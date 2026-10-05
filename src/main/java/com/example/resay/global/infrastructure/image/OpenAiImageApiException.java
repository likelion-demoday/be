package com.example.resay.global.infrastructure.image;

import java.time.Duration;
import lombok.Getter;

@Getter
public class OpenAiImageApiException extends RuntimeException {

    private final Integer statusCode;
    private final String errorCode;
    private final boolean retryable;
    private final Duration retryAfter;
    private final String requestId;
    private final String providerMessage;

    public OpenAiImageApiException(
            Integer statusCode,
            String errorCode,
            boolean retryable,
            Duration retryAfter,
            String requestId,
            String providerMessage,
            Throwable cause
    ) {
        super(buildMessage(statusCode, errorCode, requestId, providerMessage), cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
        this.requestId = requestId;
        this.providerMessage = providerMessage;
    }

    private static String buildMessage(
            Integer statusCode,
            String errorCode,
            String requestId,
            String providerMessage
    ) {
        String message = "OpenAI 이미지 생성 API 호출에 실패했습니다."
                + " statusCode=" + statusCode
                + ", errorCode=" + errorCode
                + ", requestId=" + requestId;
        if (providerMessage == null || providerMessage.isBlank()) {
            return message;
        }
        return message + ", providerMessage=" + providerMessage;
    }
}
