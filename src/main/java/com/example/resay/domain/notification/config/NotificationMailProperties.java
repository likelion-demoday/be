package com.example.resay.domain.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 메일 안의 링크가 여는 주소.
// frontendBaseUrl: 버튼이 여는 프론트 (운영: https://resay.site)
// apiBaseUrl: 로그인 없이 누르는 수신 거부 링크 (운영: https://api.resay.site)
@ConfigurationProperties(prefix = "app")
public record NotificationMailProperties(String frontendBaseUrl, String apiBaseUrl) {

    private static final String DEFAULT_FRONTEND_BASE_URL = "http://localhost:5173";
    private static final String DEFAULT_API_BASE_URL = "http://localhost:8080";

    public NotificationMailProperties {
        frontendBaseUrl = normalize(frontendBaseUrl, DEFAULT_FRONTEND_BASE_URL);
        apiBaseUrl = normalize(apiBaseUrl, DEFAULT_API_BASE_URL);
    }

    private static String normalize(String url, String defaultUrl) {
        return url == null || url.isBlank() ? defaultUrl : url.replaceAll("/+$", "");
    }
}
