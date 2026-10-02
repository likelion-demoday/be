package com.example.resay.global.exception;

import com.example.resay.global.apiPayload.code.status.GeneralErrorCode;
import java.time.Duration;
import lombok.Getter;

@Getter
public class RateLimitExceededException extends GeneralException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(Duration retryAfter) {
        super(GeneralErrorCode.TOO_MANY_REQUESTS);
        // 1초 미만이 0으로 내려가 "지금 바로 다시 시도"로 읽히지 않게 올림한다
        this.retryAfterSeconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
