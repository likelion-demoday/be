package com.example.resay.global.infrastructure.liner;

import java.time.Duration;
import lombok.Getter;

@Getter
public class LinerApiException extends RuntimeException {

    private final Integer statusCode;
    private final String errorCode;
    private final boolean retryable;
    private final Duration retryAfter;
    private final String requestId;

    public LinerApiException(
            Integer statusCode,
            String errorCode,
            boolean retryable,
            Duration retryAfter,
            String requestId,
            Throwable cause
    ) {
        super("LINER API 호출에 실패했습니다.", cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
        this.requestId = requestId;
    }
}
