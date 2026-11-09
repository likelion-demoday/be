package com.example.resay.domain.share.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "share")
public record ShareProperties(String frontendBaseUrl, Duration validity) {

    private static final String DEFAULT_FRONTEND_BASE_URL = "http://localhost:5173";
    private static final Duration DEFAULT_VALIDITY = Duration.ofDays(7);

    public ShareProperties {
        frontendBaseUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? DEFAULT_FRONTEND_BASE_URL
                : frontendBaseUrl.replaceAll("/+$", "");
        validity = validity == null ? DEFAULT_VALIDITY : validity;
        if (validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException("공유 링크 유효기간은 0보다 커야 합니다.");
        }
    }
}
