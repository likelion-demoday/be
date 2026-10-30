package com.example.resay.global.infrastructure.liner;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "liner")
public record LinerProperties(
        String apiKey,
        String baseUrl,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        Retry retry
) {

    private static final String DEFAULT_BASE_URL = "https://platform.liner.com";
    private static final String DEFAULT_MODEL = "liner-mark-1.1";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(300);
    private static final Retry DEFAULT_RETRY = new Retry(3, Duration.ofMillis(200), Duration.ofSeconds(2));

    public LinerProperties {
        baseUrl = hasText(baseUrl) ? baseUrl : DEFAULT_BASE_URL;
        model = hasText(model) ? model : DEFAULT_MODEL;
        connectTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        readTimeout = readTimeout != null ? readTimeout : DEFAULT_READ_TIMEOUT;
        retry = retry != null ? retry : DEFAULT_RETRY;
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
    }

    public boolean isConfigured() {
        return hasText(apiKey);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requirePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + "은(는) 양수여야 합니다.");
        }
    }

    public record Retry(int maxAttempts, Duration initialDelay, Duration maxDelay) {

        public Retry {
            if (maxAttempts < 1 || maxAttempts > 5) {
                throw new IllegalArgumentException("maxAttempts는 1 이상 5 이하여야 합니다.");
            }
            if (initialDelay == null || initialDelay.isNegative()) {
                throw new IllegalArgumentException("initialDelay는 0 이상이어야 합니다.");
            }
            if (maxDelay == null || maxDelay.isNegative() || maxDelay.compareTo(initialDelay) < 0) {
                throw new IllegalArgumentException("maxDelay는 initialDelay 이상이어야 합니다.");
            }
        }
    }
}
