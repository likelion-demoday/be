package com.example.resay.global.infrastructure.liner;

import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LinerApiRetryExecutor {

    private static final int MAX_ATTEMPTS = 2;
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(500);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(10);

    private final Sleeper sleeper;

    public LinerApiRetryExecutor() {
        this(duration -> Thread.sleep(duration.toMillis()));
    }

    LinerApiRetryExecutor(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    public LinerChatResult execute(
            LinerAnalysisStage stage,
            Supplier<LinerChatResult> request
    ) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return request.get();
            } catch (LinerApiException exception) {
                if (attempt == MAX_ATTEMPTS || !shouldRetry(exception)) {
                    throw exception;
                }
                Duration delay = retryDelay(exception);
                log.warn(
                        "LINER 호출을 재시도합니다: stage={}, attempt={}, delayMs={}, statusCode={}, errorCode={}, requestId={}",
                        stage,
                        attempt + 1,
                        delay.toMillis(),
                        exception.getStatusCode(),
                        exception.getErrorCode(),
                        exception.getRequestId()
                );
                if (!sleep(delay)) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("LINER 재시도 횟수 계산이 올바르지 않습니다.");
    }

    private boolean shouldRetry(LinerApiException exception) {
        Integer statusCode = exception.getStatusCode();
        return exception.isRetryable()
                || statusCode != null && (statusCode == 429 || statusCode >= 500);
    }

    private Duration retryDelay(LinerApiException exception) {
        Duration requested = exception.getRetryAfter();
        if (requested == null || requested.isNegative() || requested.isZero()) {
            return DEFAULT_RETRY_DELAY;
        }
        return requested.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : requested;
    }

    private boolean sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
