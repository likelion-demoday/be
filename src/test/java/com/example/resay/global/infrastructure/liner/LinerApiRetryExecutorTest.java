package com.example.resay.global.infrastructure.liner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinerApiRetryExecutorTest {

    @Test
    void retriesRetryableFailureOnce() {
        List<Duration> delays = new ArrayList<>();
        LinerApiRetryExecutor executor = new LinerApiRetryExecutor(delays::add);
        AtomicInteger attempts = new AtomicInteger();
        LinerChatResult expected = result();

        LinerChatResult actual = executor.execute(LinerAnalysisStage.INITIAL, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw exception(503, true, Duration.ofSeconds(2));
            }
            return expected;
        });

        assertThat(actual).isSameAs(expected);
        assertThat(attempts).hasValue(2);
        assertThat(delays).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void doesNotRetryNonRetryableClientFailure() {
        AtomicInteger attempts = new AtomicInteger();
        LinerApiRetryExecutor executor = new LinerApiRetryExecutor(duration -> {
        });
        LinerApiException exception = exception(401, false, null);

        assertThatThrownBy(() -> executor.execute(LinerAnalysisStage.REVIEW, () -> {
            attempts.incrementAndGet();
            throw exception;
        })).isSameAs(exception);

        assertThat(attempts).hasValue(1);
    }

    @Test
    void capsProviderRetryDelay() {
        List<Duration> delays = new ArrayList<>();
        LinerApiRetryExecutor executor = new LinerApiRetryExecutor(delays::add);
        AtomicInteger attempts = new AtomicInteger();

        executor.execute(LinerAnalysisStage.REVIEW, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw exception(429, false, Duration.ofMinutes(1));
            }
            return result();
        });

        assertThat(delays).containsExactly(Duration.ofSeconds(10));
    }

    private LinerApiException exception(
            Integer statusCode,
            boolean retryable,
            Duration retryAfter
    ) {
        return new LinerApiException(
                statusCode,
                "temporary_error",
                retryable,
                retryAfter,
                "request-1",
                null
        );
    }

    private LinerChatResult result() {
        return new LinerChatResult(
                "request-2",
                new LinerChatResponse(
                        "completion-2",
                        "liner-mark-1.1",
                        List.of(new LinerChatResponse.Choice(
                                0,
                                new LinerChatMessage("assistant", "{}"),
                                "stop"
                        )),
                        new LinerChatResponse.Usage(1, 1, 2)
                )
        );
    }
}
