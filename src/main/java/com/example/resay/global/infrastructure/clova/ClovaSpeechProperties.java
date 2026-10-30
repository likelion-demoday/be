package com.example.resay.global.infrastructure.clova;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "clova.speech")
public record ClovaSpeechProperties(
        String invokeUrl,
        String secretKey,
        // 요청마다 이 주소 뒤에 callback 비밀값을 붙여 보낸다
        String callbackBaseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    // 최대 200MB 음성을 업로드한 뒤 작업 접수 응답을 기다리는 시간
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    public ClovaSpeechProperties {
        connectTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        readTimeout = readTimeout != null ? readTimeout : DEFAULT_READ_TIMEOUT;
    }

    // 비동기 요청은 callback 주소가 없으면 CLOVA가 거부한다
    public boolean isConfigured() {
        return hasText(invokeUrl) && hasText(secretKey) && hasText(callbackBaseUrl);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
