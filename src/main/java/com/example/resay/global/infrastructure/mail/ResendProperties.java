package com.example.resay.global.infrastructure.mail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "resend")
public record ResendProperties(
        String apiKey,
        String baseUrl,
        // 보내는 사람. 도메인 인증 전에는 Resend 기본 주소(onboarding@resend.dev)만 쓸 수 있고, 가입 계정 주소로만 보내진다
        String from,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final String DEFAULT_BASE_URL = "https://api.resend.com";
    private static final String DEFAULT_FROM = "RE:SAY <onboarding@resend.dev>";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);

    public ResendProperties {
        baseUrl = hasText(baseUrl) ? baseUrl : DEFAULT_BASE_URL;
        from = hasText(from) ? from : DEFAULT_FROM;
        connectTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        readTimeout = readTimeout != null ? readTimeout : DEFAULT_READ_TIMEOUT;
    }

    public boolean isConfigured() {
        return hasText(apiKey);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
